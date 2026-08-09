package org.example.ragandimage.domain.imagerag.config;

import org.example.ragandimage.domain.imagerag.model.ImageDocumentEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

// [Step 2-3] 이미지 전용 VectorStore & EmbeddingModel 빈 구성
@Configuration
public class ImageRagConfig {

    @Bean
    public ImageDocumentEmbeddingModel imageEmbeddingModel(@Qualifier("googleGenAiTextEmbedding") EmbeddingModel model) {
        return new ImageDocumentEmbeddingModel(model);
    }

    @Bean
    public VectorStore imageVectorStore(JdbcTemplate jdbcTemplate, ImageDocumentEmbeddingModel imageEmbeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, imageEmbeddingModel)
                .vectorTableName("image_vector_store")
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(true)
                .build();
    }
}
