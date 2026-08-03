package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** R1-020: DesignSystemProfile 버전별 저장·조회. */
@Slf4j
@Repository
public class DesignSystemProfileRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public DesignSystemProfileRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
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
            CREATE TABLE IF NOT EXISTS AI_DESIGN_SYSTEM_PROFILE (
                PROFILE_ID       VARCHAR(64) NOT NULL,
                PROFILE_VERSION  VARCHAR(32) NOT NULL,
                PROFILE_STATUS   VARCHAR(32) NOT NULL,
                PROFILE_JSON     LONGTEXT NOT NULL,
                CREATED_AT       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                UPDATED_AT       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
                PRIMARY KEY (PROFILE_ID, PROFILE_VERSION)
            )
            """);
        log.info("AI_DESIGN_SYSTEM_PROFILE 테이블 초기화 완료");
    }

    public void save(DesignSystemProfile profile) {
        jdbcTemplate.update("""
            INSERT INTO AI_DESIGN_SYSTEM_PROFILE
                (PROFILE_ID, PROFILE_VERSION, PROFILE_STATUS, PROFILE_JSON)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                PROFILE_STATUS = VALUES(PROFILE_STATUS), PROFILE_JSON = VALUES(PROFILE_JSON), UPDATED_AT = CURRENT_TIMESTAMP
            """,
                profile.id(), profile.version(), profile.status().name(), toJson(profile));
    }

    public Optional<DesignSystemProfile> findLatest(String profileId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT PROFILE_JSON FROM AI_DESIGN_SYSTEM_PROFILE
             WHERE PROFILE_ID = ? ORDER BY CREATED_AT DESC LIMIT 1
            """, String.class, profileId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public Optional<DesignSystemProfile> findVersion(String profileId, String version) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT PROFILE_JSON FROM AI_DESIGN_SYSTEM_PROFILE
             WHERE PROFILE_ID = ? AND PROFILE_VERSION = ?
            """, String.class, profileId, version);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    private String toJson(DesignSystemProfile profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (Exception e) {
            throw new IllegalStateException("DesignSystemProfile JSON 직렬화 실패", e);
        }
    }

    private DesignSystemProfile fromJson(String json) {
        try {
            return objectMapper.readValue(json, DesignSystemProfile.class);
        } catch (Exception e) {
            throw new IllegalStateException("DesignSystemProfile JSON 역직렬화 실패", e);
        }
    }
}
