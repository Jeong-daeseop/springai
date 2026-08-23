package com.krdevops.springai.service.observability;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class PipelineInfrastructureSmokeServiceTest {
    @Test void unavailableDatabase_isReportedFailClosed() {
        var jdbc = mock(JdbcTemplate.class); when(jdbc.queryForObject("SELECT 1", Integer.class)).thenThrow(new RuntimeException());
        var report = new PipelineInfrastructureSmokeService(jdbc, mock(StringRedisTemplate.class)).check();
        assertThat(report.databaseReady()).isFalse();
        assertThat(report.redisReady()).isFalse();
        assertThat(new PipelineInfrastructureSmokeService(jdbc, mock(StringRedisTemplate.class)).isReady()).isFalse();
    }

    @Test void healthyDatabaseAndRedis_areReportedReady_andRedisConnectionIsClosed() {
        var jdbc = mock(JdbcTemplate.class);
        var redis = mock(StringRedisTemplate.class);
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");

        var report = new PipelineInfrastructureSmokeService(jdbc, redis).check();

        assertThat(report.databaseReady()).isTrue();
        assertThat(report.redisReady()).isTrue();
        verify(connection).close();
    }

    @Test void redisPingFailure_isFailClosed_andConnectionIsStillClosed() {
        var jdbc = mock(JdbcTemplate.class);
        var redis = mock(StringRedisTemplate.class);
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new IllegalStateException("redis down"));

        var report = new PipelineInfrastructureSmokeService(jdbc, redis).check();

        assertThat(report.databaseReady()).isTrue();
        assertThat(report.redisReady()).isFalse();
        assertThat(report.message()).isEqualTo("Redis 연결 실패");
        verify(connection).close();
    }
}
