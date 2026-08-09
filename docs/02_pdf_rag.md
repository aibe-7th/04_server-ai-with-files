# 1부 — PDF RAG (80분)

## 1. 실습 개요
- PDF 문서 검증 후 800 토큰 단위 청크 분할 및 Supabase PgVector(`vector_store`) 저장.
- `similaritySearch`로 저장 결과 검증 후 `QuestionAnswerAdvisor` 기반 답변 생성 및 근거 표시.

---

## 2. 필요한 파일 목록

| 구분 | 파일 경로 | 역할 |
| :--- | :--- | :--- |
| Validator | `domain/rag/validator/PdfValidator.java` | PDF 시그니처(`%PDF-`) 및 텍스트 추출 검증 |
| Service | `domain/rag/service/PdfIngestService.java` | PDF 파싱, 청크 분할, VectorStore 저장 |
| Service | `domain/rag/service/RagQueryService.java` | 유사도 검색 및 답변 생성 |
| Controller | `domain/rag/controller/RagDocumentController.java` | 업로드 & 질문 엔드포인트 |
| View | `templates/rag/index.html` | PDF 업로드, 질문 입력 및 답변/출처 화면 |

---

## 3. 핵심 코드 및 단계별 실습

### Step 1-1: `PdfValidator.java`
```java
// [Step 1-1] PDF 문서 확장자, 시그니처 및 텍스트 extraction 검증기
@Component
public class PdfValidator {

    /**
     * [Step 1-1] 업로드 파일이 비어있는지 및 %PDF- 헤더 시그니처 검증
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 PDF 파일이 비어 있습니다.");
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[5];
            int read = is.read(header);
            if (read < 5 || !"%PDF-".equals(new String(header, StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("유효한 PDF 파일이 아닙니다 (PDF Header Signature 미일치).");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF 파일 검증 중 오류가 발생했습니다: %s".formatted(e.getMessage()));
        }
    }

    /**
     * [Step 1-1] 스캔된 이미지 전용 PDF 방지 (텍스트 길이 최소값 검증)
     */
    public void validateExtractedTextLength(String text) {
        if (text == null || text.trim().length() < 50) {
            throw new IllegalArgumentException("텍스트를 추출할 수 없는 스캔본 PDF입니다. 2부 Image RAG를 이용해주세요.");
        }
    }
}
```

---

### Step 1-2: `PdfIngestService.java`
```java
// [Step 1-2] PDF 수집, 분할, VectorStore 저장 서비스
@Service
@RequiredArgsConstructor
public class PdfIngestService {

    private final VectorStore vectorStore;
    private final PdfValidator pdfValidator;

    public int ingestPdf(MultipartFile file) throws IOException {
        // [Step 1-2.1] PDF 기본 유효성 검증
        pdfValidator.validate(file);

        // [Step 1-2.2] PDF Document Reader로 페이지 추출
        Resource resource = new InputStreamResource(file.getInputStream());
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                resource, PdfDocumentReaderConfig.builder().build());
        List<Document> pages = reader.read();

        /*
         * [초기 실습 단계 미사용 코드 흔적]
         * 초기 단계를 위한 단순 페이지 전체 출력 확인 코드
         * System.out.println("추출된 전체 페이지 수: " + pages.size());
         */

        // [Step 1-2.3] 텍스트 길이 최소 기준 검증
        String fullText = pages.stream().map(Document::getText).reduce("", (a, b) -> a + b);
        pdfValidator.validateExtractedTextLength(fullText);

        // [Step 1-2.4] TokenTextSplitter로 청크 분할 (기본 800 토큰)
        List<Document> chunks = new TokenTextSplitter().apply(pages);

        // [Step 1-2.5] 메타데이터 보강 (원본 파일명 기록)
        chunks.forEach(chunk -> chunk.getMetadata().put("fileName", file.getOriginalFilename()));

        // [Step 1-2.6] Supabase PgVector에 저장
        vectorStore.add(chunks);
        return chunks.size();
    }
}
```

---

### Step 1-3 & Step 1-4: `RagQueryService.java`
```java
// [Step 1-3 & Step 1-4] 유사도 검색 및 RAG 질의응답 서비스
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final AiProperties aiProperties;

    private static final String RAG_SYSTEM_PROMPT = """
            제공된 검색 문서(Context)에 기반해서만 답변하세요.
            제공된 문서에서 답을 찾을 수 없다면 "관련 정보를 찾을 수 없습니다."라고 솔직하게 답변하세요.
            """;

    /**
     * [Step 1-3] 저장 결과 직접 검증용 유사도 검색
     */
    public List<Document> searchSimilarDocuments(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(aiProperties.rag().topK())
                .similarityThreshold(aiProperties.rag().similarityThreshold())
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * [Step 1-4] QuestionAnswerAdvisor 기반 RAG 답변 생성
     */
    public RagResponse askQuestion(String question) {
        /*
         * [이전 실습 단계 미사용 코드 흔적]
         * Step 1-3에서 similaritySearch 결과만 먼저 눈으로 확인해보던 코드입니다.
         * List<Document> rawSearchResults = searchSimilarDocuments(question);
         * System.out.println("조회된 청크 수: " + rawSearchResults.size());
         */

        // ChatClient 호출 시 QuestionAnswerAdvisor를 통해 PgVector 문서 자동 주입
        String answer = chatClientBuilder.build().prompt()
                .system(RAG_SYSTEM_PROMPT)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .user(question)
                .call()
                .content();

        // 화면 표출을 위해 근거 문서 검색 결과를 함께 반환
        List<Document> sources = searchSimilarDocuments(question);
        return new RagResponse(answer, sources);
    }

    public record RagResponse(String answer, List<Document> sources) {}
}
```

---

### Step 1-5: `RagDocumentController.java` & `index.html`

#### `RagDocumentController.java`
```java
// [Step 1-5] PDF RAG 웹 컨트롤러
@Controller
@RequestMapping("/rag")
@RequiredArgsConstructor
public class RagDocumentController {

    private final PdfIngestService pdfIngestService;
    private final RagQueryService ragQueryService;

    @GetMapping
    public String index() {
        return "rag/index";
    }

    @PostMapping("/documents")
    public String uploadPdf(@RequestParam("file") MultipartFile file, Model model) {
        try {
            int count = pdfIngestService.ingestPdf(file);
            model.addAttribute("message", "%d개 청크 저장 완료".formatted(count));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "rag/index";
    }

    @PostMapping("/ask")
    public String askQuestion(@RequestParam("question") String question, Model model) {
        RagQueryService.RagResponse response = ragQueryService.askQuestion(question);
        model.addAttribute("question", question);
        model.addAttribute("answer", response.answer());
        model.addAttribute("sources", response.sources());
        return "rag/index";
    }
}
```

#### `index.html`
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>PDF RAG</title>
</head>
<body>
    <a th:href="@{/}">← 홈</a>
    <h1>1부: PDF RAG</h1>

    <div style="display: flex; flex-direction: column; gap: 20px;">
        <!-- 업로드 폼 -->
        <form th:action="@{/rag/documents}" method="post" enctype="multipart/form-data">
            <input type="file" name="file" accept="application/pdf" required />
            <button type="submit">업로드</button>
        </form>
        <p th:if="${message}" th:text="${message}" style="color: green;"></p>
        <p th:if="${error}" th:text="${error}" style="color: red;"></p>

        <!-- 질문 폼 -->
        <form th:action="@{/rag/ask}" method="post">
            <input type="text" name="question" style="width: 300px;" th:value="${question}" placeholder="질문 입력" required />
            <button type="submit">질문</button>
        </form>

        <!-- 답변 및 근거 -->
        <div th:if="${answer}">
            <h3>답변: <span th:text="${answer}"></span></h3>
            <h4>근거 문서:</h4>
            <ul>
                <li th:each="source : ${sources}">
                    <span th:text="${source.metadata['fileName']}"></span>
                    <p th:text="${source.text}"></p>
                </li>
            </ul>
        </div>
    </div>
</body>
</html>
```

---

## 4. 검증 체크포인트 ✅
- PDF 업로드 시 `vector_store` 행 생성 확인
- 질문에 대한 답변 및 출처 파일명이 정상 표시되는지 확인
