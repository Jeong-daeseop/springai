package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.ScreenSpecification;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
public class ScreenSpecRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public ScreenSpecRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) {
            return;
        }
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_SCREEN_SPECIFICATION (
                SPEC_ID       VARCHAR(64) NOT NULL,
                SPEC_VERSION  INT NOT NULL,
                SPEC_STATUS   VARCHAR(32) NOT NULL,
                DATABASE_NAME VARCHAR(100) NOT NULL,
                PRIMARY_TABLE VARCHAR(128) NOT NULL,
                SPEC_JSON     LONGTEXT NOT NULL,
                CREATED_AT    DATETIME DEFAULT CURRENT_TIMESTAMP,
                UPDATED_AT    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                PRIMARY KEY (SPEC_ID, SPEC_VERSION)
            )
            """);
        log.info("AI_SCREEN_SPECIFICATION 테이블 초기화 완료");
    }

    public void save(ScreenSpecification specification) {
        jdbcTemplate.update("""
            INSERT INTO AI_SCREEN_SPECIFICATION
                (SPEC_ID, SPEC_VERSION, SPEC_STATUS, DATABASE_NAME, PRIMARY_TABLE, SPEC_JSON)
            VALUES (?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                SPEC_STATUS = VALUES(SPEC_STATUS), SPEC_JSON = VALUES(SPEC_JSON), UPDATED_AT = CURRENT_TIMESTAMP
            """,
                specification.id(), specification.version(), specification.status().name(),
                specification.database(), specification.primaryTable(), toJson(specification));
    }

    /** 승인된 계약을 기존 버전과 충돌 없이 신규 Snapshot으로 저장한다. */
    public void saveImmutable(ScreenSpecification specification) {
        Optional<ScreenSpecification> existing = findVersion(specification.id(), specification.version());
        if (existing.isPresent() && !existing.get().equals(specification)) {
            throw new IllegalStateException("SCREEN_SPEC_VERSION_CONFLICT: "
                    + specification.id() + "/" + specification.version());
        }
        if (existing.isEmpty()) {
            jdbcTemplate.update("""
                INSERT INTO AI_SCREEN_SPECIFICATION
                    (SPEC_ID, SPEC_VERSION, SPEC_STATUS, DATABASE_NAME, PRIMARY_TABLE, SPEC_JSON)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                    specification.id(), specification.version(), specification.status().name(),
                    specification.database(), specification.primaryTable(), toJson(specification));
        }
    }

    public Optional<ScreenSpecification> findLatest(String id) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SPEC_JSON FROM AI_SCREEN_SPECIFICATION
             WHERE SPEC_ID = ? ORDER BY SPEC_VERSION DESC LIMIT 1
            """, String.class, id);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public Optional<ScreenSpecification> findVersion(String id, int version) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SPEC_JSON FROM AI_SCREEN_SPECIFICATION
             WHERE SPEC_ID = ? AND SPEC_VERSION = ?
            """, String.class, id, version);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    private String toJson(ScreenSpecification specification) {
        try {
            return objectMapper.writeValueAsString(specification);
        } catch (Exception e) {
            throw new IllegalStateException("화면명세 JSON 직렬화 실패", e);
        }
    }

    private ScreenSpecification fromJson(String json) {
        try {
            return objectMapper.readValue(json, ScreenSpecification.class);
        } catch (Exception e) {
            throw new IllegalStateException("화면명세 JSON 역직렬화 실패", e);
        }
    }
}
