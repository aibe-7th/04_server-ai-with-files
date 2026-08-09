package org.example.ragandimage.domain.rag.service;

import lombok.RequiredArgsConstructor;
import org.example.ragandimage.global.config.AiProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;

// [Step 1-3 & Step 1-4] 유사도 검색 및 RAG 질의응답 서비스
@Service
@RequiredArgsConstructor
public class RagQueryService {

    private final ChatClient.Builder chatClientBuilder;
    private final VectorStore vectorStore;
    private final AiProperties aiProperties;

    private static final String RAG_SYSTEM_PROMPT = """
            제공된 검색 문서(Context)에 기반해서만 답변하세요.
            제공된 문서에서 답을 찾을 수 없다면 "관련 정보를 찾을 수 없습니다."라고 솔직하게 답변하세요.
            """;

    /**
     * [Step 1-3] 저장 결과 직접 검증용 유사도 검색
     */
    public List<Document> searchSimilarDocuments(String question) {
        SearchRequest request = SearchRequest.builder()
                .query(question)
                .topK(aiProperties.rag().topK())
                .similarityThreshold(aiProperties.rag().similarityThreshold())
                .build();
        return vectorStore.similaritySearch(request);
    }

    /**
     * [Step 1-4] QuestionAnswerAdvisor 기반 RAG 답변 생성
     */
    public RagResponse askQuestion(String question) {
        /*
         * [이전 실습 단계 미사용 코드 흔적]
         * Step 1-3에서 similaritySearch 결과만 먼저 눈으로 확인해보던 코드입니다.
         * List<Document> rawSearchResults = searchSimilarDocuments(question);
         * System.out.println("조회된 청크 수: " + rawSearchResults.size());
         */

        // ChatClient 호출 시 QuestionAnswerAdvisor를 통해 PgVector 문서자동 주입
        String answer = chatClientBuilder.build().prompt()
                .system(RAG_SYSTEM_PROMPT)
                .advisors(QuestionAnswerAdvisor.builder(vectorStore).build())
                .user(question)
                .call()
                .content();

        // 화면 표출을 위해 근거 문서 검색 결과를 함께 반환
        List<Document> sources = searchSimilarDocuments(question);
        return new RagResponse(answer, sources);
    }

    public record RagResponse(String answer, List<Document> sources) {}
}
