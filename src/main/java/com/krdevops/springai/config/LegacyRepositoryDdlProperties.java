package com.krdevops.springai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ARCH-0310: Flyway(V1)로 전환한 뒤에도 기존 Repository의 {@code @PostConstruct}
 * {@code CREATE TABLE IF NOT EXISTS}를 즉시 제거하지 않고 이 flag 뒤로 옮긴다.
 * 기본값 {@code true}로 기존 동작을 그대로 유지하며, 전체 환경 검증 후(ARCH-0311)
 * {@code false}로 내린 뒤 최종적으로 DDL 코드를 제거한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.db")
public class LegacyRepositoryDdlProperties {
    private boolean legacyRepositoryDdlEnabled = true;
}
