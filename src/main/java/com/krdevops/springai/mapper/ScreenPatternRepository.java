package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 화면 Pattern·Slot 계약의 버전별 불변 저장소. */
@Repository
public class ScreenPatternRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public ScreenPatternRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) return;
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_SCREEN_PATTERN (
                PATTERN_ID VARCHAR(64) NOT NULL,
                PATTERN_VERSION VARCHAR(32) NOT NULL,
                PATTERN_STATUS VARCHAR(32) NOT NULL DEFAULT 'PUBLISHED',
                PATTERN_JSON LONGTEXT NOT NULL,
                CREATED_AT DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                PRIMARY KEY (PATTERN_ID, PATTERN_VERSION)
            )
            """);
    }

    public void saveImmutable(ScreenPatternDefinition pattern) {
        Optional<ScreenPatternDefinition> existing = findVersion(pattern.pattern(), pattern.version());
        if (existing.isPresent()) {
            if (existing.get().equals(pattern)) return;
            throw new IllegalStateException("SCREEN_PATTERN_VERSION_CONFLICT: "
                    + pattern.pattern().code() + "/" + pattern.version());
        }
        jdbcTemplate.update("""
            INSERT INTO AI_SCREEN_PATTERN (PATTERN_ID, PATTERN_VERSION, PATTERN_STATUS, PATTERN_JSON)
            VALUES (?, ?, ?, ?)
            """, pattern.pattern().code(), pattern.version(), pattern.status().name(), toJson(pattern));
    }

    public Optional<ScreenPatternDefinition> findLatest(ScreenPattern pattern) {
        List<String> values = jdbcTemplate.queryForList("""
            SELECT PATTERN_JSON FROM AI_SCREEN_PATTERN
             WHERE PATTERN_ID = ? AND PATTERN_STATUS = 'PUBLISHED'
             ORDER BY CREATED_AT DESC LIMIT 1
            """, String.class, pattern.code());
        return values.isEmpty() ? Optional.empty() : Optional.of(fromJson(values.get(0)));
    }

    public Optional<ScreenPatternDefinition> findVersion(ScreenPattern pattern, String version) {
        List<String> values = jdbcTemplate.queryForList("""
            SELECT PATTERN_JSON FROM AI_SCREEN_PATTERN WHERE PATTERN_ID = ? AND PATTERN_VERSION = ?
            """, String.class, pattern.code(), version);
        return values.isEmpty() ? Optional.empty() : Optional.of(fromJson(values.get(0)));
    }

    public ScreenPatternDefinition transition(
            ScreenPattern pattern, String version,
            ScreenPatternDefinition.Status expected, ScreenPatternDefinition.Status target) {
        ScreenPatternDefinition current = findVersion(pattern, version)
                .orElseThrow(() -> new IllegalArgumentException("Screen Pattern 버전을 찾을 수 없습니다: " + pattern.code() + "/" + version));
        if (current.status() != expected) {
            throw new IllegalStateException("SCREEN_PATTERN_INVALID_TRANSITION: " + current.status() + " -> " + target);
        }
        ScreenPatternDefinition changed = new ScreenPatternDefinition(current.pattern(), current.version(), target, current.slots());
        int updated = jdbcTemplate.update("""
            UPDATE AI_SCREEN_PATTERN SET PATTERN_STATUS = ?, PATTERN_JSON = ?
             WHERE PATTERN_ID = ? AND PATTERN_VERSION = ? AND PATTERN_STATUS = ?
            """, target.name(), toJson(changed), pattern.code(), version, expected.name());
        if (updated != 1) throw new IllegalStateException("SCREEN_PATTERN_CONCURRENT_TRANSITION");
        return changed;
    }

    private String toJson(ScreenPatternDefinition value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("ScreenPattern JSON 직렬화 실패", exception); }
    }

    private ScreenPatternDefinition fromJson(String value) {
        try { return objectMapper.readValue(value, ScreenPatternDefinition.class); }
        catch (Exception exception) { throw new IllegalStateException("ScreenPattern JSON 역직렬화 실패", exception); }
    }
}
