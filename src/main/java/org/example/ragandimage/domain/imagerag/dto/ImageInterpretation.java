package org.example.ragandimage.domain.imagerag.dto;

import java.util.List;

// [Step 2-2] Gemini Vision 멀티모달 분석 구조화 정보 DTO
public record ImageInterpretation(
        String caption,
        List<String> tags,
        String ocrText
) {}
