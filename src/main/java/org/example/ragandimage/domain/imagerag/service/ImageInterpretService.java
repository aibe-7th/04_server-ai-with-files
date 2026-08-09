package org.example.ragandimage.domain.imagerag.service;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.imagerag.dto.ImageInterpretation;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.content.Media;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;

// [Step 2-2] Gemini Vision 호출 및 이미지 해석 서비스
@Service
@RequiredArgsConstructor
public class ImageInterpretService {

    private final ChatClient.Builder chatClientBuilder;

    private static final String PROMPT = """
            이미지를 상세히 분석하여 다음 정보들을 추출하세요:
            1. caption: 이미지 전체에 대한 상세 요약 설명
            2. tags: 객체 및 분위기 키워드 태그 목록
            3. ocrText: 이미지 내부의 간판, 영수증, 표지판 등에 적힌 모든 텍스트 (글자가 없으면 빈값)
            """;

    public ImageInterpretation interpret(byte[] imageBytes, String mimeType) {
        Media media = new Media(MimeTypeUtils.parseMimeType(mimeType), new ByteArrayResource(imageBytes));

        /*
         * [구조화 추출 이전 단순 텍스트 출력 방식 흔적]
         * String rawAnswer = chatClientBuilder.build().prompt()
         *         .user(u -> u.text("이미지를 설명해주세요.").media(media))
         *         .call().content();
         */

        return chatClientBuilder.build().prompt()
                .user(u -> u.text(PROMPT).media(media))
                .call()
                .entity(ImageInterpretation.class);
    }
}
