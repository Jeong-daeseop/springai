package com.krdevops.springai.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.IntStream;

/**
 * CI 러너에는 로컬 전용 ko-sroberta ONNX 모델 파일도, Redis 서버도 없어 실제
 * {@code TransformersEmbeddingModel}/{@code RedisVectorStore} 생성이 각각 파일 I/O와 연결
 * 단계에서 실패한다. RAG 기능과 무관한 테스트에서 이 설정을 {@code @Import}하면
 * {@code @ConditionalOnMissingBean}에 의해 임베딩 auto-configuration이 이 no-op 스텁으로
 * 대체되고(단, 이 조건은 구현 클래스 기준이라 spring.ai.model.embedding=none과 함께 써야 함),
 * {@code VectorStoreConfig.vectorStore()}는 같은 빈 이름(allow-bean-definition-overriding)으로
 * 덮어써 Redis 연결 자체를 시도하지 않게 한다. {@code RagService} 등 이 Bean에 의존하는 나머지
 * 컴포넌트도 그대로 연결된다.
 */
@TestConfiguration
public class StubEmbeddingModelTestConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        return new EmbeddingModel() {
            @Override
            public EmbeddingResponse call(EmbeddingRequest request) {
                List<String> instructions = request.getInstructions();
                List<Embedding> embeddings = IntStream.range(0, instructions.size())
                        .mapToObj(index -> new Embedding(new float[]{0f}, index))
                        .toList();
                return new EmbeddingResponse(embeddings);
            }

            @Override
            public float[] embed(Document document) {
                return new float[]{0f};
            }
        };
    }

    @Bean
    public VectorStore vectorStore() {
        return new VectorStore() {
            @Override
            public void add(List<Document> documents) {
            }

            @Override
            public void delete(List<String> idList) {
            }

            @Override
            public void delete(Filter.Expression filterExpression) {
            }

            @Override
            public List<Document> similaritySearch(SearchRequest request) {
                return List.of();
            }
        };
    }
}
