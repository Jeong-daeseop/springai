package com.krdevops.springai.service.observability;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** 운영 프로파일에서 DB·Redis 연결을 실제 호출로 확인하는 smoke 경계. */
@Service
public class PipelineInfrastructureSmokeService {
    private final JdbcTemplate jdbc;
    private final StringRedisTemplate redis;
    public PipelineInfrastructureSmokeService(JdbcTemplate jdbc, StringRedisTemplate redis) { this.jdbc = jdbc; this.redis = redis; }
    public SmokeReport check() {
        try { jdbc.queryForObject("SELECT 1", Integer.class); }
        catch (RuntimeException ex) { return new SmokeReport(false, false, "DB 연결 실패"); }
        try (var connection = redis.getConnectionFactory().getConnection()) { connection.ping(); }
        catch (RuntimeException ex) { return new SmokeReport(true, false, "Redis 연결 실패"); }
        return new SmokeReport(true, true, "OK");
    }
    public boolean isReady() { SmokeReport report = check(); return report.databaseReady() && report.redisReady(); }
    public record SmokeReport(boolean databaseReady, boolean redisReady, String message) { }
}
