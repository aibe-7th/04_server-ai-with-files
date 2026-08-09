package org.example.ragandimage.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

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
