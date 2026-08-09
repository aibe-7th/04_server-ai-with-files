package org.example.ragandimage.domain.imagegen.config;

import org.example.ragandimage.global.config.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

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
