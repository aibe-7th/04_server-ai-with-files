package org.example.ragandimage.domain.imagerag.validator;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.List;

// [Step 2-2] 이미지 MIME 타입 및 파일 손상 검증기
@Component
public class ImageValidator {

    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png");

    public void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 이미지 파일이 비어 있습니다.");
        }
        if (!ALLOWED.contains(file.getContentType())) {
            throw new IllegalArgumentException("PNG/JPEG 형식의 이미지만 업로드할 수 있습니다.");
        }
        if (ImageIO.read(file.getInputStream()) == null) {
            throw new IllegalArgumentException("손상되었거나 올바르지 않은 이미지 파일입니다.");
        }
    }
}
