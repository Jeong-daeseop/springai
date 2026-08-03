package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ARCH-0310: {@code app.db.legacy-repository-ddl-enabled=false}이면 Repository의
 * {@code @PostConstruct} DDL이 실행되지 않아야 한다(Flyway가 대신 스키마를 관리하는 이후 단계
 * 대비). 9개 Repository가 모두 같은 스크립트로 동일하게 삽입한 guard라
 * {@code DesignAnalysisRepository}로 대표 검증한다 — mock {@code JdbcTemplate}으로 실제 DB
 * 연결 없이 빠르게 확인한다.
 */
class LegacyRepositoryDdlFlagTest {

    @Test
    void disabledFlag_skipsDdlExecution() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyRepositoryDdlProperties properties = new LegacyRepositoryDdlProperties();
        properties.setLegacyRepositoryDdlEnabled(false);

        DesignAnalysisRepository repository =
                new DesignAnalysisRepository(jdbcTemplate, new ObjectMapper(), properties);
        repository.createTableIfNotExists();

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void enabledFlag_stillExecutesDdl() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LegacyRepositoryDdlProperties properties = new LegacyRepositoryDdlProperties();
        // 기본값 true — Flyway 도입 직후에도 기존 동작을 그대로 유지해야 한다.

        DesignAnalysisRepository repository =
                new DesignAnalysisRepository(jdbcTemplate, new ObjectMapper(), properties);
        repository.createTableIfNotExists();

        verify(jdbcTemplate).execute("""
            CREATE TABLE IF NOT EXISTS AI_DESIGN_ANALYSIS (
                ANALYSIS_ID   VARCHAR(64) PRIMARY KEY,
                SOURCE_HASH   VARCHAR(64) NOT NULL,
                PROVIDER_ID   VARCHAR(32) NOT NULL,
                MODEL_ID      VARCHAR(100) NOT NULL,
                PROMPT_VERSION VARCHAR(32) NOT NULL,
                RESULT_JSON   LONGTEXT NOT NULL,
                CREATED_AT    DATETIME DEFAULT CURRENT_TIMESTAMP,
                UNIQUE KEY UK_DESIGN_ANALYSIS_CACHE (SOURCE_HASH, PROVIDER_ID, MODEL_ID, PROMPT_VERSION)
            )
            """);
    }
}
