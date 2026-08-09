package org.example.ragandimage.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

// [Step 0-4] S3 퍼블릭 URL 대신 자체 컨트롤러(프록시) 방식으로 미디어를 내려주는 다운로드 컨트롤러
@RestController
@RequiredArgsConstructor
public class MediaDownloadController {

    private final ObjectStorageService objectStorageService;

    @GetMapping("/media/download")
    public ResponseEntity<InputStreamResource> downloadMedia(@RequestParam("key") String key) {
        InputStream inputStream = objectStorageService.downloadStream(key);
        InputStreamResource resource = new InputStreamResource(inputStream);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"%s\"".formatted(key))
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
}
