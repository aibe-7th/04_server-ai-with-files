package org.example.ragandimage.domain.imagegen.model;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.imagegen.dto.CloudflareImageDtos;
import org.example.ragandimage.global.config.AiProperties;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

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
