# 4시간 실습 계획안 — PDF RAG · Image RAG · 이미지 생성

> 이 문서는 **실습안 자체가 아니라, 실습안을 작성하기 위한 계획**입니다.
> 강의 자료(405-1 / 405-2 / 405-3)의 범위를 벗어나지 않는 선에서, 4시간 안에 끝낼 수 있는 분량으로 재구성합니다.

---

## 1. 전제 조건

### 1.1 현재 프로젝트 상태

`build.gradle` 기준 이미 확보된 것들입니다.

| 항목 | 값 |
| :--- | :--- |
| Spring Boot | 4.1.0 |
| Spring AI BOM | 2.0.0 |
| Java toolchain | 17 |
| 웹·화면 | `spring-boot-starter-webmvc`, `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation` |
| 영속성 | `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` (runtimeOnly) |
| AI | `spring-ai-starter-model-google-genai`, `spring-ai-starter-model-google-genai-embedding`, `spring-ai-starter-model-openai`, `spring-ai-starter-vector-store-pgvector`, `spring-ai-vector-store-advisor`, `spring-ai-pdf-document-reader` |
| 기타 | Lombok, DevTools |

- `src/main/resources/application.yaml`에는 `spring.application.name`만 있고, 나머지는 실습에서 채웁니다.
- 소스는 `org.example.ragandimage` 패키지 하나뿐인 빈 상태입니다.

### 1.2 추가해야 할 의존성

이미지 생성 결과와 업로드 원본을 Supabase Storage(S3 호환)에 저장하기 위해 하나만 추가합니다.

```groovy
implementation 'io.awspring.cloud:spring-cloud-aws-starter-s3:4.1.0'
```

> 이미지 생성(405-3)은 Cloudflare Workers AI를 `RestClient`로 직접 호출하므로 **추가 스타터가 필요 없습니다.**
> `RestClient`는 이미 들어온 `spring-boot-starter-webmvc`에 포함됩니다.

### 1.3 외부 서비스 사전 준비 (수강생이 실습 전에 완료)

| 서비스 | 준비물 | 확인 방법 |
| :--- | :--- | :--- |
| Supabase (Postgres) | 프로젝트 생성, 연결 문자열 | Dashboard → Database → Extensions에서 `vector` 확장 **활성화** |
| Supabase (Storage) | 버킷 1개(예: `media`), S3 access key / secret key, S3 endpoint · region | Project Settings → Storage → S3 Connection |
| Google AI Studio | `GOOGLE_AI_API_KEY` | 무료 Tier RPD 한도 확인 |
| Cloudflare | `CF_ACCOUNT_ID`, Workers AI 실행 권한만 부여한 `CF_API_TOKEN` | Workers AI 모델 목록에서 FLUX 존재 확인 |

- 모든 자격 증명은 **환경변수로만** 주입합니다. `application.yaml`에는 `${...}` 참조만 씁니다.
- Supabase Storage는 S3 호환 엔드포인트를 제공하므로 `spring-cloud-aws-starter-s3`의 `S3Client`에 `endpoint`·`region`·`path-style-access: true`를 지정해 그대로 사용합니다.

### 1.4 실습 소재

- PDF: 정책브리핑 보도자료 (https://www.korea.kr/briefing/pressReleaseList.do) 중 **텍스트 기반** 5~10페이지 분량 1~2건
- 이미지: 글자가 들어간 사진(간판·표지판·영수증·차트) 2~3장 + 일반 풍경 사진 2~3장
  - OCR 효과를 눈으로 확인하려면 글자 있는 이미지가 반드시 필요합니다.

---

## 2. 4시간 타임테이블

| 구간 | 시간 | 주제 | 산출물 |
| :--- | :--- | :--- | :--- |
| 0부 | 20분 | 환경 세팅과 공통 골격 | `application.yaml`, `AiProperties`, 홈 화면 |
| 1부 | 80분 | PDF RAG (405-1) | PDF 업로드 → pgvector 저장 → 근거 있는 답변 |
| — | 10분 | 휴식 | |
| 2부 | 70분 | Image RAG (405-2) | 이미지 업로드 → 해석·임베딩 → 텍스트로 이미지 검색 |
| — | 10분 | 휴식 | |
| 3부 | 60분 | 이미지 생성 (405-3) | 프롬프트 → FLUX 생성 → 미리보기 + 선택 저장 |
| 마무리 | 10분 | 통합 확인과 정리 | 생성 이미지를 Image RAG에 태워보기 |

- 총 260분(4시간 20분) 중 휴식 20분을 빼면 **순 실습 240분**입니다.
- 각 부는 **독립 실행 가능**하도록 설계합니다. 1부에서 막힌 수강생도 2부 시작 시점의 완성 코드를 받아 이어갈 수 있게 단계별 체크포인트를 둡니다.

---

## 3. 0부 — 환경 세팅과 공통 골격 (20분)

### 3.1 할 일

1. `build.gradle`에 `spring-cloud-aws-starter-s3:4.1.0` 추가 후 리로드
2. 환경변수 등록 (IntelliJ Run Configuration 또는 `.env`)
   - `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`
   - `SUPABASE_S3_ENDPOINT`, `SUPABASE_S3_REGION`, `SUPABASE_S3_ACCESS_KEY`, `SUPABASE_S3_SECRET_KEY`, `SUPABASE_S3_BUCKET`
   - `GOOGLE_AI_API_KEY`, `CF_ACCOUNT_ID`, `CF_API_TOKEN`
3. `application.yaml` 기본 블록 작성 (datasource, JPA, multipart, Spring AI)
4. `@ConfigurationProperties` 바인딩 클래스 `AiProperties` 작성 + `@EnableConfigurationProperties` 등록
5. 애플리케이션 기동으로 DB 연결과 `vector_store` 테이블 자동 생성 확인

### 3.2 `application.yaml` 설계 (전체 실습 공통, 미리 한 번에 작성)

```yaml
spring:
  application:
    name: rag-and-image
  datasource:
    url: ${SUPABASE_DB_URL}
    username: ${SUPABASE_DB_USER}
    password: ${SUPABASE_DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
  cloud:
    aws:
      credentials:
        access-key: ${SUPABASE_S3_ACCESS_KEY}
        secret-key: ${SUPABASE_S3_SECRET_KEY}
      region:
        static: ${SUPABASE_S3_REGION}
      s3:
        endpoint: ${SUPABASE_S3_ENDPOINT}
        path-style-access-enabled: true
  ai:
    model:
      embedding: google-genai          # 기본 EmbeddingModel은 텍스트용 유지
    google:
      genai:
        api-key: ${GOOGLE_AI_API_KEY}
        chat:
          model: ${app.ai.google.primary-model}
        embedding:
          text:
            model: gemini-embedding-001
            dimensions: 1536
    openai:
      api-key: ${GOOGLE_AI_API_KEY}    # Gemini 키를 OpenAI 호환 계층에 그대로 사용
      embedding:
        base-url: https://generativelanguage.googleapis.com/v1beta/openai
    vectorstore:
      pgvector:
        initialize-schema: true
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1536
        table-name: vector_store

app:
  storage:
    bucket: ${SUPABASE_S3_BUCKET}
  ai:
    google:
      primary-model: gemini-3.5-flash-lite
      fallback-model: gemini-3.1-flash-lite
    rag:
      top-k: 5
      similarity-threshold: 0.7
    image:
      allowed-mime-types: image/png, image/jpeg
      max-dimension-px: 1536
    image-rag:
      top-k: 8
      similarity-threshold: 0.5
    image-generation:
      account-id: ${CF_ACCOUNT_ID}
      api-token: ${CF_API_TOKEN}
      base-url: https://api.cloudflare.com/client/v4
      model: "@cf/black-forest-labs/flux-1-schnell"
      steps: 4
      timeout: 60s
```

**작성 시 강조할 지점**
- `@cf/`로 시작하는 모델 ID는 YAML 예약 문자 `@` 때문에 **따옴표 필수**
- `spring.ai.openai.embedding.base-url`에는 `v1beta/openai`까지 **직접 포함** (스타터가 `/embeddings`를 뒤에 붙임)
- `spring.ai.model.embedding: google-genai`로 기본 임베딩 빈을 텍스트용으로 고정
- `remove-existing-vector-store-table`은 쓰지 않음 (데이터 손실)

### 3.3 `AiProperties` 설계

```java
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        Google google,
        Rag rag,
        Image image,
        ImageRag imageRag,
        ImageGeneration imageGeneration
) {
    public record Google(String primaryModel, String fallbackModel) {}
    public record Rag(int topK, double similarityThreshold) {}
    public record Image(List<String> allowedMimeTypes, int maxDimensionPx) {}
    public record ImageRag(int topK, double similarityThreshold) {}
    public record ImageGeneration(String accountId, String apiToken,
            String baseUrl, String model, int steps, Duration timeout) {}
}
```

- `@Value` 대신 쓰는 이유(타입 안정성, 기동 시점 검증)를 한 번 짚고 넘어갑니다.
- 별도로 `StorageProperties(prefix = "app.storage")`를 두어 버킷명을 바인딩합니다.

### 3.4 공통 유틸 (실습 내내 재사용, 여기서 미리 작성)

| 클래스 | 역할 |
| :--- | :--- |
| `ObjectStorageService` | `S3Client`로 바이트 업로드 → 키 반환, 키로 다운로드, 키 삭제 |
| `GlobalExceptionHandler` | `@ControllerAdvice`로 업로드/AI 예외를 사용자 메시지로 변환 |
| `HomeController` + `home.html` | 세 실습으로 가는 링크만 있는 인덱스 |

### 3.5 체크포인트 ✅
- 애플리케이션 기동 시 오류 없음
- Supabase에 `vector_store` 테이블과 HNSW 인덱스가 자동 생성됨
- 홈 화면(`/`) 접속 확인

---

## 4. 1부 — PDF RAG (80분)

### 4.1 진행 순서

| 단계 | 시간 | 내용 |
| :--- | :--- | :--- |
| 1-1 | 15분 | 개념: RAG가 필요한 이유, 문서·청크·임베딩·벡터 스토어, 1536차원을 맞춰야 하는 이유 |
| 1-2 | 20분 | 업로드 → ETL → 저장 구현 |
| 1-3 | 15분 | `similaritySearch`로 저장 결과 검증 |
| 1-4 | 15분 | `QuestionAnswerAdvisor`로 답변 생성 + 출처 표시 |
| 1-5 | 15분 | PDF 검증·중복 업로드 방어, 모델 fallback |

### 4.2 만들 클래스

| 클래스 | 역할 |
| :--- | :--- |
| `RagDocumentController` | `GET /rag` 폼, `POST /rag/documents` 업로드, `POST /rag/ask` 질의 |
| `PdfIngestService` | 검증 → `PagePdfDocumentReader` → `TokenTextSplitter` → `vectorStore.add(...)` |
| `RagQueryService` | `similaritySearch` 결과 조회 + `QuestionAnswerAdvisor` 답변 생성 |
| `PdfValidator` | 크기·시그니처(`%PDF-`)·추출 텍스트 길이 검증 |
| `templates/rag/index.html` | 업로드 폼 + 질문 폼 + 답변/근거 출력 |

### 4.3 핵심 코드 흐름 (실습안에서 단계별로 타이핑할 대상)

```java
// 1) 업로드 스트림을 그대로 읽기 — 별도 임시 파일 복사 없음
Resource resource = new InputStreamResource(file.getInputStream());
PagePdfDocumentReader reader = new PagePdfDocumentReader(
        resource, PdfDocumentReaderConfig.builder().build());

// 2) 페이지 Document → 청크 분할 (기본 800 토큰)
List<Document> pages = reader.read();
List<Document> chunks = new TokenTextSplitter().apply(pages);

// 3) 메타데이터 보강 후 저장
chunks.forEach(d -> d.getMetadata().put("fileName", file.getOriginalFilename()));
vectorStore.add(chunks);
```

```java
// 4) 저장 검증 — 답변보다 검색을 먼저 본다
SearchRequest request = SearchRequest.builder()
        .query(question)
        .topK(aiProperties.rag().topK())
        .similarityThreshold(aiProperties.rag().similarityThreshold())
        .build();
List<Document> results = vectorStore.similaritySearch(request);
```

```java
// 5) RAG 답변
String answer = chatClient.prompt()
        .system(RAG_SYSTEM_PROMPT)   // "검색된 문서만 근거로. 없으면 모른다고 답하라"
        .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
        .user(question)
        .call()
        .content();
```

### 4.4 실습에서 반드시 다룰 주의점

- **검증**: `accept` 속성과 Content-Type은 보안 수단이 아님 → 서버에서 크기 제한 + `%PDF-` 시그니처 + 파싱 성공 확인
- **스캔 PDF 차단**: 공백 제외 추출 텍스트 길이가 임곗값 미만이면 저장 중단 → "OCR/멀티모달 대상"으로 안내 (2부 예고)
- **중복 업로드**: 파일 바이트 해시를 메타데이터에 남기고, 재업로드 시 `vectorStore.delete(...)` 후 재저장
- **fallback**: `isModelQuotaExceeded(...)`(cause 체인에서 HTTP 429 + `RESOURCE_EXHAUSTED` 확인)일 때만 `GoogleGenAiChatOptions`로 fallback 모델 1회 호출
  - 인증 실패·잘못된 요청·안전 필터는 fallback 금지
- **임곗값 0.7은 시작점**: 답변이 이상하면 `similaritySearch` → 청크 크기 → `topK` → `similarityThreshold` 순으로 점검

### 4.5 체크포인트 ✅
- 보도자료 PDF 업로드 후 Supabase `vector_store`에 행이 쌓임
- `similaritySearch`가 질문과 관련된 문단을 반환
- 답변 화면에 파일명·페이지 번호가 근거로 함께 표시됨
- 문서에 없는 질문에는 "모른다"고 답함

---

## 5. 2부 — Image RAG (70분)

### 5.1 진행 순서

| 단계 | 시간 | 내용 |
| :--- | :--- | :--- |
| 2-1 | 15분 | 개념: 멀티모달, Base64/Data URI(+33%), OCR, 두 산출물을 함께 저장하는 이유 |
| 2-2 | 15분 | 이미지 검증·리사이즈 + `Media` 첨부 해석(`ImageInterpretation`) |
| 2-3 | 15분 | 이미지 전용 임베딩 빈 · 어댑터 · `imageVectorStore` 구성 |
| 2-4 | 15분 | 업로드 파이프라인 결합 (스토리지 저장 + 벡터 저장) |
| 2-5 | 10분 | 크로스모달 검색과 결과 화면 |

### 5.2 만들 클래스

| 클래스 | 역할 |
| :--- | :--- |
| `ImageRagConfig` | `imageApiEmbeddingModel` 빈, `imageVectorStore` 빈 |
| `ImageDocumentEmbeddingModel` | `getEmbeddingContent(Document)`를 Data URI로 바꿔주는 어댑터 |
| `ImageValidator` | 매직 넘버 판별, `width × height` 상한 확인 |
| `ImageResizer` | 최대 변 1536px로 축소 후 재인코딩 (`ImageIO`) |
| `ImageInterpretService` | `Media` 첨부 `ChatClient` 호출 → `ImageInterpretation` |
| `ImageIngestService` | 검증 → 해시 중복 확인 → 리사이즈 → 스토리지 저장 → 해석 → `imageVectorStore.add(...)` |
| `ImageSearchService` | 소유자 필터 + `similaritySearch` → 화면용 DTO |
| `ImageRagController`, `templates/image-rag/index.html` | 업로드 폼 · 검색 폼 · 썸네일/점수 출력 |

### 5.3 핵심 코드 흐름

```java
// 해석: 캡션 + 태그 + OCR을 한 번에 구조화 추출
Media media = new Media(MimeTypeUtils.parseMimeType(detectedMimeType),
        new ByteArrayResource(resizedBytes));

ImageInterpretation result = chatClient.prompt()
        .user(u -> u.text(INTERPRET_PROMPT).media(media))
        .call()
        .entity(ImageInterpretation.class);
```

```java
// 임베딩 입력만 이미지로 바꾸는 어댑터
@Override
public String getEmbeddingContent(Document document) {
    Media media = document.getMedia();
    if (media == null) return Objects.requireNonNull(document.getText());
    String base64 = Base64.getEncoder().encodeToString(media.getDataAsByteArray());
    return "data:%s;base64,%s".formatted(media.getMimeType(), base64);
}
```

```java
// 이미지 전용 스토어 — 텍스트 vector_store와 반드시 분리
PgVectorStore.builder(jdbcTemplate, imageEmbeddingModel)
        .vectorTableName("image_vector_store")
        .dimensions(1536)
        .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
        .indexType(PgVectorStore.PgIndexType.HNSW)
        .initializeSchema(true)
        .build();
```

```java
// 저장: 이미지는 미디어 본문, 해석 텍스트는 메타데이터
Document imageDocument = Document.builder()
        .media(media)
        .metadata("interpretation", interpretationText)
        .metadata("objectKey", objectKey)
        .metadata("ownerId", ownerId)
        .metadata("mimeType", detectedMimeType)
        .metadata("contentHash", contentHash)
        .build();
imageVectorStore.add(List.of(imageDocument));
```

```java
// 검색: 소유자 필터는 문자열 보간 대신 빌더로
Filter.Expression ownerFilter = new FilterExpressionBuilder()
        .eq("ownerId", ownerId).build();
SearchRequest.builder()
        .query(question)
        .topK(aiProperties.imageRag().topK())
        .similarityThreshold(aiProperties.imageRag().similarityThreshold())
        .filterExpression(ownerFilter)
        .build();
```

### 5.4 강의 자료 범위 안에서의 단순화 결정

- **소유자(`ownerId`)**: Spring Security는 아직 도입 전이므로, 폼에서 받거나 세션에 고정한 **더미 사용자 ID**를 사용합니다. "실무에서는 인증 주체로 대체한다"는 점만 명시합니다.
- **답변 생성 방식**: 두 방식 중 **해석 텍스트 재사용**만 구현합니다. 원본 재첨부는 비용·시간 문제로 설명만 하고 넘어갑니다.
- **하이브리드 검색**: 개념 소개만 하고 구현하지 않습니다.

### 5.5 반드시 다룰 주의점

- 이미지 1장은 독립 검색 단위 → **청크 분할하지 않음**
- 원본 바이너리를 DB에 넣지 않음(`bytea` 금지) → **스토리지 키만** 보관
- Data URI는 임베딩 요청 만들 때만 사용, **메타데이터에 저장 금지**
- 텍스트/이미지 `EmbeddingModel` 빈이 둘이므로 `@Qualifier` 명시 필수
- 해석·임베딩·DB 저장이 실패하면 **이미 올린 스토리지 객체를 삭제**
- 임곗값은 1부의 0.7을 그대로 쓰지 않고 **0.5 전후로 재측정**
- 개인정보: 해석 텍스트(OCR 포함)는 원본보다 유출이 쉬움 → 로그에 남기지 않음

### 5.6 체크포인트 ✅
- `image_vector_store` 테이블이 생성되고 업로드마다 행이 쌓임
- 글자 있는 사진에서 `ocrText`가 실제로 채워짐
- "간판에 적힌 가게 이름"처럼 **OCR 없이는 못 찾을 질의**로 검색 성공
- 다른 `ownerId`로 검색하면 결과가 나오지 않음

---

## 6. 3부 — 이미지 생성 (60분)

### 6.1 진행 순서

| 단계 | 시간 | 내용 |
| :--- | :--- | :--- |
| 3-1 | 10분 | 개념: 확산 모델·잠재 확산·`steps`/`guidance`/`seed`, 증류 모델 |
| 3-2 | 10분 | 왜 OpenAI 스타터로는 안 되는가 → 커스텀 `ImageModel` 선택 |
| 3-3 | 20분 | `RestClient` 빈 + DTO + `CloudflareImageModel` 구현 |
| 3-4 | 10분 | 프롬프트 작성법과 LLM 보강(`PromptEnhancer`) |
| 3-5 | 10분 | 폼·서비스·결과 화면 연결, 선택 저장 |

### 6.2 만들 클래스

| 클래스 | 역할 |
| :--- | :--- |
| `WorkersAiConfig` | base URL · `Authorization` 헤더 · 타임아웃 고정한 `RestClient` 빈 |
| `CloudflareImageRequest/Response/Result/Error` | Cloudflare `result` 래퍼 구조 매핑 record |
| `CloudflareImageOptions` | `ImageOptions` 구현, model·steps·seed |
| `CloudflareImageModel` | `ImageModel.call(ImagePrompt)` 구현 |
| `PromptEnhancer` | `ChatClient` + `.entity(EnhancedPrompt.class)` |
| `ImageGenerationService` | 보강 → 생성 → (선택) 디코딩·스토리지 저장 |
| `ImageForm`, `ImageGenerationController`, `templates/images/form.html` | 폼·검증·결과 표시 |

### 6.3 반드시 다룰 함정

- **모델 ID 인코딩**: `.uri("/ai/run/{model}", model)`로 넘기면 `/`가 인코딩되어 404 → 경로 문자열을 직접 이어붙임
- **HTTP 200이어도 실패**: 응답 `success == false` + `errors` 배열 확인 필수
- **`retrieve()`는 4xx/5xx에서 예외** → 애플리케이션 예외로 변환
- **타임아웃 필수**: 무제한 대기는 요청 스레드를 고갈시킴
- **`RestClient.Builder` 주입**: `RestClient.create()`로 만들면 전역 관측성 설정이 빠짐
- **`b64Json`에는 순수 Base64만**, `data:image/jpeg;base64,` 접두사는 화면에서 조립
- **Base64를 세션/hidden field로 들고 다니지 않음** → 미리보기와 선택 저장을 **한 요청**에서 처리
- **seed 저장**: seed + 프롬프트를 함께 저장해야 재현 가능. 이미지 자체를 캐시 키로 쓸 수 없음

### 6.4 저장 시 메타데이터

`prompt` / `enhancedPrompt` / `model` / `steps` / `seed` / `createdAt` / `objectKey`
→ JPA 엔티티 `GeneratedImage` 하나로 Supabase Postgres에 저장, 바이너리는 Storage에.

### 6.5 체크포인트 ✅
- 한국어 프롬프트 입력 → 보강된 영어 프롬프트가 화면에 표시됨
- 이미지가 Data URI로 즉시 렌더링됨
- `저장` 체크 시 Storage에 객체가 생기고 DB에 seed·프롬프트가 남음
- 같은 seed + 같은 프롬프트로 재생성 시 동일한 이미지 확인

---

## 7. 마무리 (10분) — 세 실습을 하나로

- 3부에서 생성·저장한 이미지를 2부의 Image RAG 업로드 파이프라인에 태워 **생성 → 해석 → 검색**이 하나로 이어지는 것을 확인합니다.
- 전체 데이터 배치를 한 장으로 정리:

| 저장소 | 담는 것 |
| :--- | :--- |
| Supabase Postgres `vector_store` | PDF 청크 텍스트 + 텍스트 임베딩(1536) |
| Supabase Postgres `image_vector_store` | 해석 텍스트 메타데이터 + 이미지 임베딩(1536) |
| Supabase Postgres `generated_image` | 생성 프롬프트·seed·모델·스토리지 키 |
| Supabase Storage | 원본 이미지 / 생성 이미지 바이너리 |

- 다루지 않은 것 예고: 인증(Spring Security)과 소유자 정책, 비동기 배치 처리, 하이브리드 검색, 모듈러 RAG(`RetrievalAugmentationAdvisor`), AI Gateway

---

## 8. 실습안 작성 시 지킬 원칙

1. **범위 고정**: 전달된 405-1/2/3 자료에 나온 API·설정·클래스만 사용합니다. 자료에 없는 라이브러리(Tesseract, LangChain4j 등)는 도입하지 않습니다.
2. **점진적 완성**: 각 부는 "동작하는 최소 버전 → 검증 → 주의점 반영" 순서로 진행합니다. 처음부터 완성형 코드를 붙여넣지 않습니다.
3. **검색 먼저, 답변 나중**: RAG 두 파트 모두 `similaritySearch` 결과를 눈으로 확인한 뒤 답변 생성으로 넘어갑니다. 디버깅 습관을 심는 것이 목적입니다.
4. **체크포인트 코드 제공**: 각 부 시작 시점의 완성 코드를 브랜치나 스냅샷으로 준비해, 앞 단계에서 막힌 수강생이 이어갈 수 있게 합니다.
5. **API 한도 대비**: 무료 Tier RPD 한도에 걸릴 수 있으므로 업로드 PDF는 10페이지 이하, 이미지는 5장 이하로 제한하고, 한도 소진 시 fallback 동작을 오히려 실습 소재로 씁니다.
6. **자격 증명 노출 금지**: 모든 예제 코드에서 키는 환경변수 참조로만 등장시킵니다.
7. **비용·지연 인지**: 이미지 해석·생성은 응답이 느리다는 점을 미리 안내하고, 버튼 비활성화가 사용량 제한이 아님을 명시합니다.

---

## 9. 리스크와 대응

| 리스크 | 영향 | 대응 |
| :--- | :--- | :--- |
| Google 무료 Tier RPD 소진 | 2·3부 진행 불가 | 실습 전 소진 여부 확인, fallback 모델 설정, 조를 나눠 키 분산 |
| Supabase `vector` 확장 미활성화 | 1부 시작부터 막힘 | 0부 체크포인트에서 전원 확인 후 진행 |
| Supabase Storage S3 연결 실패 | 2·3부 저장 단계 실패 | `path-style-access-enabled: true` 누락이 최다 원인, 0부에서 업로드 스모크 테스트 |
| Cloudflare 모델 카탈로그 변동 | 3부 404 | 실습 직전 모델 목록 확인, 모델 ID는 설정으로 분리해 즉시 교체 |
| PDF가 스캔 이미지 | 1부에서 텍스트 0건 | 소재 PDF를 강사가 미리 검증해 배포 |
| 시간 초과 | 3부 미완 | 3부 3-4(프롬프트 보강)를 선택 과제로 돌려 20분 확보 |

---

## 10. 다음 산출물

이 계획을 바탕으로 작성할 문서입니다.

| 파일 | 내용 |
| :--- | :--- |
| `docs/01_setup.md` | 0부 — 의존성·환경변수·`application.yaml`·공통 유틸 |
| `docs/02_pdf_rag.md` | 1부 — PDF RAG 단계별 실습안 |
| `docs/03_image_rag.md` | 2부 — Image RAG 단계별 실습안 |
| `docs/04_image_generation.md` | 3부 — 이미지 생성 단계별 실습안 |
| `docs/99_troubleshooting.md` | 자주 나오는 오류와 해결 (차원 불일치, `@cf/` 파싱 오류, 404, 429 등) |
