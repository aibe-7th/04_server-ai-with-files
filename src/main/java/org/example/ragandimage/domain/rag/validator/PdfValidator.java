package org.example.ragandimage.domain.rag.validator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

// [Step 1-1] PDF 문서 확장자, 시그니처 및 텍스트 extraction 검증기
@Component
public class PdfValidator {

    /**
     * [Step 1-1] 업로드 파일이 비어있는지 및 %PDF- 헤더 시그니처 검증
     */
    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 PDF 파일이 비어 있습니다.");
        }

        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[5];
            int read = is.read(header);
            if (read < 5 || !"%PDF-".equals(new String(header, StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("유효한 PDF 파일이 아닙니다 (PDF Header Signature 미일치).");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF 파일 검증 중 오류가 발생했습니다: %s".formatted(e.getMessage()));
        }
    }

    /**
     * [Step 1-1] 스캔된 이미지 전용 PDF 방지 (텍스트 길이 최소값 검증)
     */
    public void validateExtractedTextLength(String text) {
        if (text == null || text.trim().length() < 50) {
            throw new IllegalArgumentException("텍스트를 추출할 수 없는 스캔본 PDF입니다. 2부 Image RAG를 이용해주세요.");
        }
    }
}
