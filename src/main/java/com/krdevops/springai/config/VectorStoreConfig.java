package com.krdevops.springai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPooled;

/**
 * Vector Store 설정 — Redis Stack (redis-stack 컨테이너)
 *
 * EmbeddingModel: TransformersEmbeddingModel (ko-sroberta-multitask ONNX)
 * → EmbeddingModel 인터페이스로 주입받아 구현체 교체에 유연하게 대응
 * → spring.ai.model.embedding=transformers 설정으로 TransformersEmbeddingModel 자동 선택
 */
@Configuration
public class VectorStoreConfig {

    @Value("${spring.ai.vectorstore.redis.uri:redis://localhost:6379}")
    private String redisUri;

    @Value("${spring.ai.vectorstore.redis.index:egov-rag}")
    private String indexName;

    @Value("${spring.ai.vectorstore.redis.prefix:egov:}")
    private String prefix;

    @Bean
    public JedisPooled jedisPooled() {
        String uri = redisUri.replace("redis://", "");
        String[] parts = uri.split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
        return new JedisPooled(host, port);
    }

    @Bean
    public RedisVectorStore vectorStore(JedisPooled jedisPooled, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(true)
                .build();
    }
}
