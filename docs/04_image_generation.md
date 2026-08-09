# 3부 — 이미지 생성 (60분)

## 1. 실습 개요
- Cloudflare Workers AI (**FLUX.1-schnell**) 모델을 `RestClient`로 연동.
- 프롬프트 자동 보강기(`PromptEnhancer`)로 한국어 → 영문 확장 프롬프트 변환.
- `CloudflareImageModel` 커스텀 빈 구현 및 Data URI 프리뷰 & Storage/JPA 저장.

---

## 2. 필요한 파일 목록

| 구분 | 파일 경로 | 역할 |
| :--- | :--- | :--- |
| DTO | `domain/imagegen/dto/CloudflareImageDtos.java` | API Response/Request DTO 매핑 |
| Config | `domain/imagegen/config/WorkersAiConfig.java` | RestClient 빈 설정 |
| Model | `domain/imagegen/model/CloudflareImageOptions.java` | ImageOptions 구현체 |
| Model | `domain/imagegen/model/CloudflareImageModel.java` | 커스텀 ImageModel 구현체 |
| Service | `domain/imagegen/service/PromptEnhancer.java` | 영문 프롬프트 확장기 |
| Entity | `domain/imagegen/entity/GeneratedImage.java` | 생성 메타데이터 JPA 엔티티 |
| Repository | `domain/imagegen/repository/GeneratedImageRepository.java` | JPA 데이터 접근 인터페이스 |
| Service | `domain/imagegen/service/ImageGenerationService.java` | 생성 및 저장 서비스 |
| Controller | `domain/imagegen/controller/ImageGenerationController.java` | 웹 엔드포인트 |
| View | `templates/images/form.html` | 생성 및 프리뷰 UI |

---

## 3. 핵심 코드 및 단계별 실습

### Step 3-1: `CloudflareImageDtos.java` & `WorkersAiConfig.java`

#### `CloudflareImageDtos.java`
```java
// [Step 3-1] Cloudflare Workers AI API 요청/응답 DTO
public class CloudflareImageDtos {
    public record Request(String prompt, Integer num_steps, Long seed) {}
    public record Response(Result result, boolean success, List<Error> errors) {}
    public record Result(String image) {}
    public record Error(int code, String message) {}
}
```

#### `WorkersAiConfig.java`
```java
// [Step 3-1] Cloudflare Workers AI 호출용 RestClient 빈 설정
@Configuration
public class WorkersAiConfig {

    @Bean
    public RestClient cfRestClient(RestClient.Builder builder, AiProperties aiProperties) {
        AiProperties.ImageGeneration gen = aiProperties.imageGeneration();
        return builder
                .baseUrl("%s/accounts/%s".formatted(gen.baseUrl(), gen.accountId()))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer %s".formatted(gen.apiToken()))
                .build();
    }
}
```

---

### Step 3-2 & Step 3-3: `CloudflareImageOptions.java` & `CloudflareImageModel.java`

#### `CloudflareImageOptions.java`
```java
// [Step 3-2] Spring AI ImageOptions 인터페이스 구현체
public record CloudflareImageOptions(Integer steps, Long seed, String model) implements ImageOptions {
    @Override public Integer getN() { return 1; }
    @Override public Integer getHeight() { return 512; }
    @Override public Integer getWidth() { return 512; }
    @Override public String getResponseFormat() { return "b64_json"; }
    @Override public String getStyle() { return null; }
    @Override public String getModel() { return model; }
}
```

#### `CloudflareImageModel.java`
```java
// [Step 3-2 & Step 3-3] Cloudflare Workers AI FLUX 커스텀 ImageModel 구현체
@Component
@RequiredArgsConstructor
public class CloudflareImageModel implements ImageModel {

    private final RestClient cfRestClient;
    private final AiProperties aiProperties;

    @Override
    public ImageResponse call(ImagePrompt imagePrompt) {
        CloudflareImageOptions options = (CloudflareImageOptions) imagePrompt.getOptions();
        String promptText = imagePrompt.getInstructions().get(0).getText();
        String modelName = aiProperties.imageGeneration().model();

        CloudflareImageDtos.Request body = new CloudflareImageDtos.Request(promptText, 4, options != null ? options.seed() : null);

        /*
         * [Spring AI OpenAI 스타터 직접 적용 시도 흔적]
         * OpenAI 스타터로는 Cloudflare의 /ai/run/@cf/... 커스텀 응답 구조와 맞지 않아 RestClient로 직접 교체 구현함.
         */

        // URL 인코딩 방지를 위해 경로 직접 결합
        String endpoint = "/ai/run/%s".formatted(modelName);

        CloudflareImageDtos.Response response = cfRestClient.post()
                .uri(endpoint)
                .body(body)
                .retrieve()
                .body(CloudflareImageDtos.Response.class);

        if (response == null || !response.success() || response.result() == null) {
            throw new RuntimeException("Cloudflare Workers AI FLUX 이미지 생성에 실패했습니다.");
        }

        return new ImageResponse(List.of(new ImageGeneration(new Image(null, response.result().image()))));
    }
}
```

---

### Step 3-4: `PromptEnhancer.java`

```java
// [Step 3-4] LLM 기반 프롬프트 자동 보강기
@Component
@RequiredArgsConstructor
public class PromptEnhancer {

    private final ChatClient.Builder chatClientBuilder;

    public String enhance(String originalPrompt) {
        return chatClientBuilder.build().prompt()
                .system("고품질 이미지 생성을 위해 사용자의 입력 문장을 상세한 영문 디퓨전 프롬프트로 변환하세요. 오직 최종 영문 프롬프트 텍스트만 출력하세요.")
                .user(originalPrompt)
                .call()
                .content();
    }
}
```

---

### Step 3-4 & Step 3-5: JPA 저장, 서비스 & Web Controller 구현

#### `GeneratedImage.java` & `GeneratedImageRepository.java`
```java
// [Step 3-4 & Step 3-5] 생성된 이미지 메타데이터 저장용 JPA 엔티티
@Entity
@Table(name = "generated_image")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class GeneratedImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String originalPrompt;
    private String enhancedPrompt;
    private String model;
    private Long seed;
    private String objectKey;
    private LocalDateTime createdAt;
}
```

```java
// [Step 3-5] GeneratedImage JPA 리포지토리
public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, Long> {}
```

#### `ImageGenerationService.java`
```java
// [Step 3-5] 이미지 생성 및 선택 저장 서비스
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private final CloudflareImageModel imageModel;
    private final PromptEnhancer promptEnhancer;
    private final ObjectStorageService objectStorageService;
    private final GeneratedImageRepository repository;

    // [Step 3-5.1] 프롬프트 보강 후 FLUX 생성 및 미리보기 DTO 리턴
    public GenerateResult generate(String prompt, Long seed) {
        String enhanced = promptEnhancer.enhance(prompt);
        long actualSeed = (seed != null) ? seed : new Random().nextLong(1_000_000_000L);

        /*
         * [초기 실습 단계 미사용 코드 흔적]
         * 생성 즉시 S3/DB에 자동 저장하던 방식은 사용자가 마음에 들지 않아도 저장되어 용량을 차단하는 문제가 있어
         * 미리보기 후 '선택 저장'하는 2단계 프로세스로 변경됨.
         */

        ImageResponse response = imageModel.call(new ImagePrompt(enhanced, new CloudflareImageOptions(4, actualSeed, null)));
        String b64 = response.getResults().get(0).getOutput().getB64Json();

        return new GenerateResult(prompt, enhanced, b64, actualSeed);
    }

    // [Step 3-5.2] 사용자가 선택한 이미지 S3 및 JPA DB 저장
    public void saveImage(String prompt, String enhancedPrompt, String base64Image, Long seed) {
        byte[] bytes = Base64.getDecoder().decode(base64Image);
        String objectKey = objectStorageService.upload("generated.png", new ByteArrayInputStream(bytes), "image/png");

        repository.save(GeneratedImage.builder()
                .originalPrompt(prompt)
                .enhancedPrompt(enhancedPrompt)
                .model("@cf/black-forest-labs/flux-1-schnell")
                .seed(seed)
                .objectKey(objectKey)
                .createdAt(LocalDateTime.now())
                .build());
    }

    public record GenerateResult(String prompt, String enhancedPrompt, String base64Image, Long seed) {}
}
```

#### `ImageGenerationController.java` & `form.html`

```java
// [Step 3-5] 이미지 생성 웹 컨트롤러
@Controller
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService service;

    @GetMapping("/generate")
    public String form() { return "images/form"; }

    @PostMapping("/generate")
    public String generate(@RequestParam("prompt") String prompt,
                           @RequestParam(value = "seed", required = false) Long seed,
                           Model model) {
        model.addAttribute("result", service.generate(prompt, seed));
        return "images/form";
    }

    @PostMapping("/save")
    public String save(@RequestParam("prompt") String prompt,
                       @RequestParam("enhancedPrompt") String enhancedPrompt,
                       @RequestParam("base64Image") String base64Image,
                       @RequestParam("seed") Long seed,
                       Model model) {
        service.saveImage(prompt, enhancedPrompt, base64Image, seed);
        model.addAttribute("message", "이미지 저장 완료");
        return "images/form";
    }
}
```

#### `form.html`
```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>이미지 생성</title>
</head>
<body>
    <a th:href="@{/}">← 홈</a>
    <h1>3부: FLUX 이미지 생성</h1>

    <div style="display: flex; flex-direction: column; gap: 20px;">
        <!-- 프롬프트 입력 -->
        <form th:action="@{/images/generate}" method="post">
            <input type="text" name="prompt" style="width: 300px;" placeholder="프롬프트 입력" required />
            <button type="submit">이미지 생성</button>
        </form>

        <!-- 결과 미리보기 & 저장 -->
        <div th:if="${result}">
            <p>확장 프롬프트: <span th:text="${result.enhancedPrompt}"></span></p>
            <div>
                <img th:src="'data:image/png;base64,' + ${result.base64Image}" style="max-width: 300px;" />
            </div>
            <form th:action="@{/images/save}" method="post">
                <input type="hidden" name="prompt" th:value="${result.prompt}" />
                <input type="hidden" name="enhancedPrompt" th:value="${result.enhancedPrompt}" />
                <input type="hidden" name="base64Image" th:value="${result.base64Image}" />
                <input type="hidden" name="seed" th:value="${result.seed}" />
                <button type="submit">선택 저장</button>
            </form>
        </div>
        <p th:if="${message}" th:text="${message}" style="color: green;"></p>
    </div>
</body>
</html>
```

---

## 4. 검증 체크포인트 ✅
- 프롬프트 입력 시 영문 프롬프트 확장 및 Base64 이미지 프리뷰 작동 확인
- `선택 저장` 클릭 시 S3 업로드 및 `generated_image` DB 엔티티 저장 확인
