package org.example.ragandimage.domain.imagerag.model;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.content.Media;

import java.util.Base64;

// [Step 2-3] Document의 Media 바이너리를 Data URI로 바꿔주는 임베딩 어댑터
@RequiredArgsConstructor
public class ImageDocumentEmbeddingModel implements EmbeddingModel {

    private final EmbeddingModel delegate;

    public String getEmbeddingContent(Document document) {
        Media media = document.getMedia();
        if (media == null) {
            return document.getText() != null ? document.getText() : "";
        }
        String base64 = Base64.getEncoder().encodeToString(media.getDataAsByteArray());
        return "data:%s;base64,%s".formatted(media.getMimeType(), base64);
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate.call(request);
    }

    @Override
    public float[] embed(Document document) {
        return delegate.embed(getEmbeddingContent(document));
    }
}
