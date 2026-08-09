package org.example.ragandimage.global.storage;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import org.example.ragandimage.global.config.StorageProperties;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.UUID;

// [Step 0-4] S3 스토리지 연동 공통 서비스 (Supabase S3 호환 연동)
@Service
@RequiredArgsConstructor
public class ObjectStorageService {

    private final S3Template s3Template;
    private final StorageProperties storageProperties;

    /**
     * S3 버킷 파일 업로드
     */
    public String upload(String originalFilename, InputStream inputStream, String contentType) {
        String key = "uploads/%s_%s".formatted(UUID.randomUUID(), originalFilename);
        s3Template.upload(storageProperties.bucket(), key, inputStream);
        return key;
    }

    /**
     * S3 버킷 파일 삭제
     */
    public void delete(String key) {
        s3Template.deleteObject(storageProperties.bucket(), key);
    }

    /**
     * S3 퍼블릭 다운로드 URL 조회 (퍼블릭 버킷 전용)
     */
    public String getUrl(String key) {
        try {
            return s3Template.download(storageProperties.bucket(), key).getURL().toString();
        } catch (Exception e) {
            throw new RuntimeException("S3 URL 조회 실패: %s".formatted(e.getMessage()));
        }
    }

    /**
     * 자체 컨트롤러(프록시)용 S3 객체 자원 다운로드
     */
    public InputStream downloadStream(String key) {
        try {
            return s3Template.download(storageProperties.bucket(), key).getInputStream();
        } catch (Exception e) {
            throw new RuntimeException("S3 스트림 다운로드 실패: %s".formatted(e.getMessage()));
        }
    }
}
