package org.example.ragandimage.domain.imagerag.service;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.domain.imagerag.dto.ImageInterpretation;
import org.example.ragandimage.domain.imagerag.util.ImageResizer;
import org.example.ragandimage.domain.imagerag.validator.ImageValidator;
import org.example.ragandimage.global.config.AiProperties;
import org.example.ragandimage.global.storage.ObjectStorageService;
import org.springframework.ai.document.Document;
import org.springframework.ai.content.Media;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.util.List;

// [Step 2-4] 이미지 업로드, 리사이즈, Storage 저장 및 image_vector_store 저장 서비스
@Service
@RequiredArgsConstructor
public class ImageIngestService {

    @Qualifier("imageVectorStore")
    private final VectorStore imageVectorStore;
    private final ObjectStorageService objectStorageService;
    private final ImageValidator imageValidator;
    private final ImageResizer imageResizer;
    private final ImageInterpretService imageInterpretService;
    private final AiProperties aiProperties;

    public String ingestImage(MultipartFile file, String ownerId) throws Exception {
        // [Step 2-4.1] 검증
        imageValidator.validate(file);

        // [Step 2-4.2] 리사이즈 (Thumbnailator 활용)
        byte[] resizedBytes = imageResizer.resize(file.getInputStream(), file.getContentType(), aiProperties.image().maxDimensionPx());

        // [Step 2-4.3] S3 Storage에 업로드
        String objectKey = objectStorageService.upload(file.getOriginalFilename(), new ByteArrayInputStream(resizedBytes), file.getContentType());

        try {
            // [Step 2-4.4] Gemini Vision 멀티모달 분석
            ImageInterpretation interpretation = imageInterpretService.interpret(resizedBytes, file.getContentType());

            // [Step 2-4.5] Document 생성 및 image_vector_store 저장
            Media media = new Media(MimeTypeUtils.parseMimeType(file.getContentType()), new ByteArrayResource(resizedBytes));

            Document doc = Document.builder()
                    .media(media)
                    .metadata("objectKey", objectKey)
                    .metadata("ownerId", ownerId)
                    .metadata("caption", interpretation.caption())
                    .metadata("ocrText", interpretation.ocrText())
                    .build();

            imageVectorStore.add(List.of(doc));
            return objectKey;

        } catch (Exception ex) {
            // 보상 트랜잭션: S3 저장 객체 롤백 삭제
            objectStorageService.delete(objectKey);
            throw ex;
        }
    }
}
