# 2부 — Image RAG (70분)

## 1. 실습 개요
- 이미지 검증/리사이즈(최대 1536px) 처리 후 Gemini Vision 멀티모달로 **OCR + 캡션 + 태그** 추출.
- 텍스트와 분리된 이미지 전용 VectorStore(`image_vector_store`) 및 Data URI 변환 `EmbeddingModel` 어댑터 구축.
- 원본은 S3 Storage에 보관하고 메타데이터/임베딩은 VectorStore에 저장하여 자연어 크로스모달 검색 구현.

---

## 2. 필요한 파일 목록

| 구분 | 파일 경로 | 역할 |
| :--- | :--- | :--- |
| Util | `domain/imagerag/validator/ImageValidator.java` | MIME 타입 및 손상 여부 검증 |
| Util | `domain/imagerag/util/ImageResizer.java` | 최대 변 1536px 축소 리사이즈 (Thumbnailator) |
| DTO | `domain/imagerag/dto/ImageInterpretation.java` | 캡션, 태그, OCR 추출 Record |
| Service | `domain/imagerag/service/ImageInterpretService.java` | Gemini Vision 호출 및 해석 추출 |
| Config | `domain/imagerag/config/ImageRagConfig.java` | 이미지 전용 VectorStore 설정 |
| Adapter | `domain/imagerag/model/ImageDocumentEmbeddingModel.java` | Data URI 변환 임베딩 어댑터 |
| Service | `domain/imagerag/service/ImageIngestService.java` | 수집/분석/저장 서비스 |
| Service | `domain/imagerag/service/ImageSearchService.java` | `ownerId` 필터 적용 유사도 검색 |
| Controller | `domain/imagerag/controller/ImageRagController.java` | 업로드 & 검색 엔드포인트 |
| View | `templates/image-rag/index.html` | 업로드/검색 UI |

---

## 3. 핵심 코드 및 단계별 실습

### Step 2-1 & Step 2-2: `ImageValidator.java` & `ImageResizer.java`

#### `ImageValidator.java`
```java
// [Step 2-2] 이미지 MIME 타입 및 파일 손상 검증기
@Component
public class ImageValidator {

    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png");

    public void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 이미지 파일이 비어 있습니다.");
        }
        if (!ALLOWED.contains(file.getContentType())) {
            throw new IllegalArgumentException("PNG/JPEG 형식의 이미지만 업로드할 수 있습니다.");
        }
        if (ImageIO.read(file.getInputStream()) == null) {
            throw new IllegalArgumentException("손상되었거나 올바르지 않은 이미지 파일입니다.");
        }
    }
}
```

#### `ImageResizer.java`
```java
// [Step 2-2] 이미지 리사이저 (Thumbnailator 오픈소스 활용)
@Component
public class ImageResizer {

    public byte[] resize(InputStream inputStream, String formatName, int maxDimensionPx) throws Exception {
        /*
         * [이전 직접 구현 시도 코드 흔적]
         * 오픈소스 도입 이전 java.awt.Graphics2D 기반 수동 리사이즈 방식
         *
         * BufferedImage original = ImageIO.read(inputStream);
         * int w = original.getWidth(), h = original.getHeight();
         * double scale = Math.min((double) maxDimensionPx / w, (double) maxDimensionPx / h);
         * int targetW = (int) (w * scale), targetH = (int) (h * scale);
         * Image scaled = original.getScaledInstance(targetW, targetH, Image.SCALE_SMOOTH);
         * BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
         * Graphics2D g2d = resized.createGraphics();
         * g2d.drawImage(scaled, 0, 0, null);
         * g2d.dispose();
         */

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(inputStream)
                .size(maxDimensionPx, maxDimensionPx)
                .outputFormat(formatName.replace("image/", ""))
                .toOutputStream(baos);
        return baos.toByteArray();
    }
}
```

---

### Step 2-2: Gemini Vision 해석 서비스

#### `ImageInterpretation.java`
```java
// [Step 2-2] Gemini Vision 멀티모달 분석 구조화 정보 DTO
public record ImageInterpretation(
        String caption,
        List<String> tags,
        String ocrText
) {}
```

#### `ImageInterpretService.java`
```java
// [Step 2-2] Gemini Vision 호출 및 이미지 해석 서비스
@Service
@RequiredArgsConstructor
public class ImageInterpretService {

    private final ChatClient.Builder chatClientBuilder;

    private static final String PROMPT = """
            이미지를 상세히 분석하여 다음 정보들을 추출하세요:
            1. caption: 이미지 전체에 대한 상세 요약 설명
            2. tags: 객체 및 분위기 키워드 태그 목록
            3. ocrText: 이미지 내부의 간판, 영수증, 표지판 등에 적힌 모든 텍스트 (글자가 없으면 빈값)
            """;

    public ImageInterpretation interpret(byte[] imageBytes, String mimeType) {
        Media media = new Media(MimeTypeUtils.parseMimeType(mimeType), new ByteArrayResource(imageBytes));

        /*
         * [구조화 추출 이전 단순 텍스트 출력 방식 흔적]
         * String rawAnswer = chatClientBuilder.build().prompt()
         *         .user(u -> u.text("이미지를 설명해주세요.").media(media))
         *         .call().content();
         */

        return chatClientBuilder.build().prompt()
                .user(u -> u.text(PROMPT).media(media))
                .call()
                .entity(ImageInterpretation.class);
    }
}
```

---

### Step 2-3: `ImageDocumentEmbeddingModel.java` & `ImageRagConfig.java`

#### `ImageDocumentEmbeddingModel.java`
```java
// [Step 2-3] Document의 Media 바이너리를 Data URI로 바꿔주는 임베딩 어댑터
@RequiredArgsConstructor
public class ImageDocumentEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;

    public String getEmbeddingContent(Document document) {
        Media media = document.getMedia();
        if (media == null) return document.getText() != null ? document.getText() : "";
        String base64 = Base64.getEncoder().encodeToString(media.getDataAsByteArray());
        return "data:%s;base64,%s".formatted(media.getMimeType(), base64);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) { return delegate.call(request); }

    @Override
    public float[] embed(Document document) { return delegate.embed(getEmbeddingContent(document)); }
}
```

#### `ImageRagConfig.java`
```java
// [Step 2-3] 이미지 전용 VectorStore & EmbeddingModel 빈 구성
@Configuration
public class ImageRagConfig {

    @Bean
    public ImageDocumentEmbeddingModel imageEmbeddingModel(@Qualifier("googleGenAiTextEmbedding") EmbeddingModel model) {
        return new ImageDocumentEmbeddingModel(model);
    }

    @Bean
    public VectorStore imageVectorStore(JdbcTemplate jdbcTemplate, ImageDocumentEmbeddingModel imageEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, imageEmbeddingModel)
                .vectorTableName("image_vector_store")
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
```

---

### Step 2-4 & Step 2-5: 수집, 검색 서비스 & Web Controller

#### `ImageIngestService.java`
```java
// [Step 2-4] 이미지 업로드, 리사이즈, Storage 저장 및 image_vector_store 저장 서비스
@Service
@RequiredArgsConstructor
public class ImageIngestService {

    @Qualifier("imageVectorStore")
    private final VectorStore imageVectorStore;
    private final ObjectStorageService objectStorageService;
    private final ImageValidator imageValidator;
    private final ImageResizer imageResizer;
    private final ImageInterpretService imageInterpretService;
    private final AiProperties aiProperties;

    public String ingestImage(MultipartFile file, String ownerId) throws Exception {
        // [Step 2-4.1] 검증
        imageValidator.validate(file);

        // [Step 2-4.2] 리사이즈 (Thumbnailator 활용)
        byte[] resizedBytes = imageResizer.resize(file.getInputStream(), file.getContentType(), aiProperties.image().maxDimensionPx());

        // [Step 2-4.3] S3 Storage에 업로드
        String objectKey = objectStorageService.upload(file.getOriginalFilename(), new ByteArrayInputStream(resizedBytes), file.getContentType());

        try {
            // [Step 2-4.4] Gemini Vision 멀티모달 분석
            ImageInterpretation interpretation = imageInterpretService.interpret(resizedBytes, file.getContentType());

            // [Step 2-4.5] Document 생성 및 image_vector_store 저장
            Media media = new Media(MimeTypeUtils.parseMimeType(file.getContentType()), new ByteArrayResource(resizedBytes));

            Document doc = Document.builder()
                    .media(media)
                    .metadata("objectKey", objectKey)
                    .metadata("ownerId", ownerId)
                    .metadata("caption", interpretation.caption())
                    .metadata("ocrText", interpretation.ocrText())
                    .build();

            imageVectorStore.add(List.of(doc));
            return objectKey;
        } catch (Exception ex) {
            // 보상 트랜잭션: S3 저장 객체 롤백 삭제
            objectStorageService.delete(objectKey);
            throw ex;
        }
    }
}
```

#### `ImageSearchService.java`
```java
// [Step 2-5] 크로스모달 이미지 검색 서비스
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    @Qualifier("imageVectorStore")
    private final VectorStore imageVectorStore;
    private final ObjectStorageService objectStorageService;
    private final AiProperties aiProperties;

    public List<ImageSearchResult> searchImages(String question, String ownerId) {
        // [Step 2-5] ownerId 필터와 함께 similaritySearch 실행
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(aiProperties.imageRag().topK())
                .similarityThreshold(aiProperties.imageRag().similarityThreshold())
                .filterExpression(new FilterExpressionBuilder().eq("ownerId", ownerId).build())
                .build();

        List<Document> documents = imageVectorStore.similaritySearch(request);

        /*
         * [미사용 고려 방식 흔적]
         * 하이브리드 검색 또는 LLM 원본 비전 재첨부 방식
         * SearchResult에 담아 LLM 프롬프트에 직접 Media로 재전달하던 방식은 비용/시간 절감을 위해 생략하고
         * 추출된 캡션/OCR 기반 빠른 리턴 방식을 선택함.
         */

        return documents.stream().map(doc -> {
            String key = (String) doc.getMetadata().get("objectKey");

            /*
             * [S3 퍼블릭 버킷 전용 직접 URL 방식 흔적]
             * String publicUrl = objectStorageService.getUrl(key);
             */

            // 자체 컨트롤러(프록시) 방식 URL 생성 (비공개 버킷 보안 지원)
            String proxyUrl = "/media/download?key=%s".formatted(key);

            return new ImageSearchResult(
                    proxyUrl,
                    (String) doc.getMetadata().get("caption"),
                    (String) doc.getMetadata().get("ocrText"),
                    doc.getScore()
            );
        }).toList();
    }

    public record ImageSearchResult(String url, String caption, String ocrText, Double score) {}
}
```

#### `ImageRagController.java` & `index.html`

```java
// [Step 2-5] Image RAG 웹 컨트롤러
@Controller
@RequestMapping("/image-rag")
@RequiredArgsConstructor
public class ImageRagController {

    private final ImageIngestService imageIngestService;
    private final ImageSearchService imageSearchService;

    @GetMapping
    public String index() { return "image-rag/index"; }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         @RequestParam(value = "ownerId", defaultValue = "userA") String ownerId,
                         Model model) {
        try {
            imageIngestService.ingestImage(file, ownerId);
            model.addAttribute("message", "이미지 분석 및 수집 완료");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "image-rag/index";
    }

    @PostMapping("/search")
    public String search(@RequestParam("query") String query,
                         @RequestParam(value = "ownerId", defaultValue = "userA") String ownerId,
                         Model model) {
        model.addAttribute("results", imageSearchService.searchImages(query, ownerId));
        return "image-rag/index";
    }
}
```

#### `index.html`
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Image RAG</title>
</head>
<body>
    <a th:href="@{/}">← 홈</a>
    <h1>2부: Image RAG</h1>

    <div style="display: flex; flex-direction: column; gap: 20px;">
        <!-- 업로드 -->
        <form th:action="@{/image-rag/upload}" method="post" enctype="multipart/form-data">
            <input type="file" name="file" accept="image/*" required />
            <button type="submit">이미지 업로드</button>
        </form>
        <p th:if="${message}" th:text="${message}" style="color: green;"></p>
        <p th:if="${error}" th:text="${error}" style="color: red;"></p>

        <!-- 검색 -->
        <form th:action="@{/image-rag/search}" method="post">
            <input type="text" name="query" placeholder="검색어 입력 (예: 간판 이름)" required />
            <button type="submit">이미지 검색</button>
        </form>

        <!-- 결과 목록 -->
        <div th:if="${results}" style="display: flex; flex-wrap: wrap; gap: 10px;">
            <div th:each="item : ${results}" style="border: 1px solid #ccc; padding: 10px; width: 200px;">
                <img th:src="${item.url}" style="width: 100%; height: auto;" />
                <p th:text="${item.caption}"></p>
                <small th:text="${item.ocrText}"></small>
            </div>
        </div>
    </div>
</body>
</html>
```

---

## 4. 검증 체크포인트 ✅
- `image_vector_store` 생성 확인
- 글자 포함 이미지 업로드 시 OCR 텍스트 기반 검색 작동 확인
