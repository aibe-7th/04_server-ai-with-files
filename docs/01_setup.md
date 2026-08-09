# 0부 — 환경 세팅과 공통 골격 (20분)

## 1. 실습 개요
- 기본 프로젝트 환경 설정 및 Supabase S3 / PgVector 연동을 준비합니다.
- `application.yaml`, 설정 바인딩 Record, 공통 스토리지 서비스, 글로벌 예외 처리기, 메인 홈 화면을 구축합니다.

---

## 2. 필요한 파일 목록

| 구분 | 파일 경로 | 역할 |
| :--- | :--- | :--- |
| Gradle | `build.gradle` | Supabase S3 연동용 AWS S3 스타터 및 Thumbnailator 추가 |
| Config | `resources/application.yaml` | Datasource, JPA, Spring AI 및 Supabase S3 설정 |
| Config | `global/config/AiProperties.java` | `app.ai` 프로퍼티 바인딩 Record |
| Config | `global/config/StorageProperties.java` | `app.storage` 프로퍼티 바인딩 Record |
| Storage | `global/storage/ObjectStorageService.java` | S3 파일 업로드/다운로드/삭제 서비스 |
| Storage | `global/storage/MediaDownloadController.java` | 자체 프록시 다운로드 컨트롤러 |
| Exception | `global/error/GlobalExceptionHandler.java` | 공통 예외 처리기 |
| Controller | `domain/home/HomeController.java` | 홈 화면 연결 컨트롤러 |
| View | `templates/home.html` | 메인 내비게이션 홈 화면 |

---

## 3. 핵심 코드 및 단계별 실습

### Step 0-1: `build.gradle` 의존성 추가
```groovy
dependencies {
    // 0부: Supabase Storage 연동 (Spring Cloud AWS S3 스타터)
    implementation 'io.awspring.cloud:spring-cloud-aws-starter-s3:4.1.0'

    // 2부: 이미지 리사이즈 오픈소스 (Thumbnailator)
    implementation 'net.coobird:thumbnailator:0.4.20'
}
```

---

### Step 0-2: `application.yaml` 설정 작성
```yaml
# [Step 0-2] 환경 세팅과 공통 설정 (application.yaml)
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
      embedding: google-genai
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
      api-key: ${GOOGLE_AI_API_KEY}
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

---

### Step 0-3: `@ConfigurationProperties` 작성

#### `AiProperties.java`
```java
// [Step 0-3] app.ai 프로퍼티 바인딩 Record
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        Google google,
        Rag rag,
        Image image,
        ImageRag imageRag,
        ImageGeneration imageGeneration
) {
    public record Google(String primaryModel) {}
    public record Rag(int topK, double similarityThreshold) {}
    public record Image(List<String> allowedMimeTypes, int maxDimensionPx) {}
    public record ImageRag(int topK, double similarityThreshold) {}
    public record ImageGeneration(String accountId, String apiToken,
                                  String baseUrl, String model, int steps, Duration timeout) {}
}
```

#### `StorageProperties.java`
```java
// [Step 0-3] app.storage 프로퍼티 바인딩 Record
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String bucket
) {}
```

#### Application 메인 클래스에 바인딩 추가 (`RagAndImageApplication.java`)
```java
// [Step 0-3] @EnableConfigurationProperties 바인딩 활성화
@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, StorageProperties.class})
public class RagAndImageApplication {
    public static void main(String[] args) {
        SpringApplication.run(RagAndImageApplication.class, args);
    }
}
```

---

### Step 0-4: 공통 S3 서비스 & 예외 처리기

#### `ObjectStorageService.java`
```java
// [Step 0-4] S3 스토리지 연동 공통 서비스 (Supabase S3 호환 연동)
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final S3Template s3Template;
    private final StorageProperties storageProperties;

    public String upload(String originalFilename, InputStream inputStream, String contentType) {
        String key = "uploads/%s_%s".formatted(UUID.randomUUID(), originalFilename);
        s3Template.upload(storageProperties.bucket(), key, inputStream);
        return key;
    }

    public void delete(String key) {
        s3Template.deleteObject(storageProperties.bucket(), key);
    }

    public String getUrl(String key) {
        try {
            return s3Template.download(storageProperties.bucket(), key).getURL().toString();
        } catch (Exception e) {
            throw new RuntimeException("S3 URL 조회 실패: %s".formatted(e.getMessage()));
        }
    }

    public InputStream downloadStream(String key) {
        try {
            return s3Template.download(storageProperties.bucket(), key).getInputStream();
        } catch (Exception e) {
            throw new RuntimeException("S3 스트림 다운로드 실패: %s".formatted(e.getMessage()));
        }
    }
}
```

#### `MediaDownloadController.java`
```java
// [Step 0-4] S3 퍼블릭 URL 대신 클린 RESTful URL 프록시 방식으로 미디어를 내려주는 다운로드 컨트롤러
@RestController
@RequiredArgsConstructor
public class MediaDownloadController {

    private final ObjectStorageService objectStorageService;

    // 클린 URL 방식 (예: GET /media/uploads/uuid_filename.jpg)
    @GetMapping("/media/{*key}")
    public ResponseEntity<InputStreamResource> downloadMedia(@PathVariable("key") String key) {
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        InputStream inputStream = objectStorageService.downloadStream(cleanKey);
        InputStreamResource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(cleanKey))
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
}
```

#### `GlobalExceptionHandler.java`
```java
// [Step 0-4] 전역 예외 처리기
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public String handleException(Exception ex, Model model) {
        model.addAttribute("errorMessage", ex.getMessage());
        return "error";
    }
}
```

---

### Step 0-5: 홈 컨트롤러 & HTML

#### `HomeController.java`
```java
// [Step 0-5] 홈 인덱스 화면 컨트롤러
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "home";
    }
}
```

#### `home.html`
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Spring AI 실습</title>
</head>
<body>
    <h1>Spring AI 실습 대시보드</h1>
    <nav style="display: flex; gap: 10px;">
        <a th:href="@{/rag}">1부: PDF RAG</a>
        <a th:href="@{/image-rag}">2부: Image RAG</a>
        <a th:href="@{/images/generate}">3부: 이미지 생성</a>
    </nav>
</body>
</html>
```

---

## 4. 검증 체크포인트 ✅
- 앱 기동 시 오류 없음
- Supabase DB에 `vector_store` 테이블 자동 생성 확인
- `http://localhost:8080/` 접속 확인
