package org.example.ragandimage.domain.imagegen.service;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.imagegen.entity.GeneratedImage;
import org.example.ragandimage.domain.imagegen.model.CloudflareImageModel;
import org.example.ragandimage.domain.imagegen.model.CloudflareImageOptions;
import org.example.ragandimage.domain.imagegen.repository.GeneratedImageRepository;
import org.example.ragandimage.global.storage.ObjectStorageService;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Random;

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
