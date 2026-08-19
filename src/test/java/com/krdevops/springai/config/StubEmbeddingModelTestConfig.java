package com.krdevops.springai.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.util.List;
import java.util.stream.IntStream;

/**
 * CI 러너에는 로컬 전용 ko-sroberta ONNX 모델 파일이 없어 실제 {@code TransformersEmbeddingModel}
 * 생성이 파일 I/O에서 실패한다. RAG 기능과 무관한 테스트에서 이 설정을 {@code @Import}하면
 * {@code @ConditionalOnMissingBean}에 의해 auto-configuration이 이 no-op 스텁으로 대체되어,
 * {@code VectorStore}/{@code RagService} 등 이 Bean에 의존하는 나머지 컴포넌트도 그대로 연결된다.
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
}
