package org.example.ragandimage.domain.imagerag.service;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.global.config.AiProperties;
import org.example.ragandimage.global.storage.ObjectStorageService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

// [Step 2-5] 크로스모달 이미지 검색 서비스
@Service
@RequiredArgsConstructor
public class ImageSearchService {

    @Qualifier("imageVectorStore")
    private final VectorStore imageVectorStore;
    private final ObjectStorageService objectStorageService;
    private final AiProperties aiProperties;

    public List<ImageSearchResult> searchImages(String question, String ownerId) {
        // [Step 2-5] ownerId 필터와 함께 similaritySearch 실행
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(aiProperties.imageRag().topK())
                .similarityThreshold(aiProperties.imageRag().similarityThreshold())
                .filterExpression(new FilterExpressionBuilder().eq("ownerId", ownerId).build())
                .build();

        List<Document> documents = imageVectorStore.similaritySearch(request);

        /*
         * [미사용 고려 방식 흔적]
         * 하이브리드 검색 또는 LLM 원본 비전 재첨부 방식
         * SearchResult에 담아 LLM 프롬프트에 직접 Media로 재전달하던 방식은 비용/시간 절감을 위해 생략하고
         * 추출된 캡션/OCR 기반 빠른 리턴 방식을 선택함.
         */

        return documents.stream().map(doc -> {
            String key = (String) doc.getMetadata().get("objectKey");
            return new ImageSearchResult(
                    objectStorageService.getUrl(key),
                    (String) doc.getMetadata().get("caption"),
                    (String) doc.getMetadata().get("ocrText"),
                    doc.getScore()
            );
        }).toList();
    }

    public record ImageSearchResult(String url, String caption, String ocrText, Double score) {}
}
