package com.krdevops.springai.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore.MetadataField;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.JedisClientConfig;
import java.net.URI;
import java.time.Duration;

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

    private final OperationalResilienceProperties resilienceProperties;

    public VectorStoreConfig(OperationalResilienceProperties resilienceProperties) {
        this.resilienceProperties = resilienceProperties;
    }

    @Bean
    public RedisClient redisClient() {
        URI uri = URI.create(redisUri);
        var builder = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(toMillis(resilienceProperties.getTimeout().getRedisConnect()))
                .socketTimeoutMillis(toMillis(resilienceProperties.getTimeout().getRedisRead()))
                .ssl("rediss".equalsIgnoreCase(uri.getScheme()));
        if (uri.getUserInfo() != null) {
            String[] credentials = uri.getUserInfo().split(":", 2);
            if (!credentials[0].isBlank()) builder.user(credentials[0]);
            if (credentials.length == 2 && !credentials[1].isBlank()) builder.password(credentials[1]);
        }
        if (uri.getPath() != null && uri.getPath().matches("/\\d+")) {
            builder.database(Integer.parseInt(uri.getPath().substring(1)));
        }
        JedisClientConfig clientConfig = builder.build();
        return RedisClient.builder().fromURI(uri).clientConfig(clientConfig).build();
    }

    private int toMillis(Duration duration) {
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, duration.toMillis()));
    }

    @Bean
    public RedisVectorStore vectorStore(RedisClient redisClient, EmbeddingModel embeddingModel) {
        return RedisVectorStore.builder(redisClient, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(true)
                .metadataFields(
                        MetadataField.tag("type"),
                        MetadataField.tag("docId")
                )
                .build();
    }
}
