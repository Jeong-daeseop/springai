package com.krdevops.springai.service.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.net.URI;
import static org.assertj.core.api.Assertions.assertThat;

/** 실제 DB·Redis가 제공되는 CI/운영 환경에서만 실행하는 live smoke. */
@Tag("live-infrastructure")
@EnabledIfEnvironmentVariable(named = "PIPELINE_INFRA_SMOKE_LIVE", matches = "true")
class PipelineInfrastructureLiveSmokeTest {
    @Test void databaseAndRedis_areReachable() {
        var dataSource = new DriverManagerDataSource(
                env("DB_URL", "jdbc:mysql://127.0.0.1:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true"),
                env("DB_USERNAME", "ebt"), env("DB_PASSWORD", "ebt01"));
        URI redisUri = URI.create(env("REDIS_URI", "redis://127.0.0.1:6379"));
        var factory = new LettuceConnectionFactory(redisUri.getHost(), redisUri.getPort() > 0 ? redisUri.getPort() : 6379);
        factory.afterPropertiesSet();
        try {
            var smoke = new PipelineInfrastructureSmokeService(new JdbcTemplate(dataSource), new StringRedisTemplate(factory));
            assertThat(smoke.isReady()).isTrue();
        } finally {
            factory.destroy();
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
