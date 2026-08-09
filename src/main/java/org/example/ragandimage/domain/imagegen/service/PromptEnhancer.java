package org.example.ragandimage.domain.imagegen.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

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
