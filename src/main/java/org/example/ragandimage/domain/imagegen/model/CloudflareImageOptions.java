package org.example.ragandimage.domain.imagegen.model;

import org.springframework.ai.image.ImageOptions;

// [Step 3-2] Spring AI ImageOptions 인터페이스 구현체
public record CloudflareImageOptions(
        Integer steps,
        Long seed,
        String model
) implements ImageOptions {

    @Override
    public Integer getN() {
        return 1;
    }

    @Override
    public Integer getHeight() {
        return 512;
    }

    @Override
    public Integer getWidth() {
        return 512;
    }

    @Override
    public String getResponseFormat() {
        return "b64_json";
    }

    @Override
    public String getStyle() {
        return null;
    }

    @Override
    public String getModel() {
        return model;
    }
}
