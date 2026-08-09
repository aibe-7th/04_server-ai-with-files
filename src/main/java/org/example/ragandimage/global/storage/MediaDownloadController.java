package org.example.ragandimage.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

// [Step 0-4] S3 퍼블릭 URL 대신 클린 RESTful URL 프록시 방식으로 미디어를 내려주는 다운로드 컨트롤러
@RestController
@RequiredArgsConstructor
public class MediaDownloadController {

    private final ObjectStorageService objectStorageService;

    // 클린 URL 방식 (예: GET /media/uploads/uuid_filename.jpg)
    @GetMapping("/media/{*key}")
    public ResponseEntity<InputStreamResource> downloadMedia(@PathVariable("key") String key) {
        String cleanKey = key.startsWith("/") ? key.substring(1) : key;
        InputStream inputStream = objectStorageService.downloadStream(cleanKey);
        InputStreamResource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(cleanKey))
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
}
