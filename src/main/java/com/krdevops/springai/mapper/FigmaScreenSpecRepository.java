package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** R1-020~022: FigmaScreenSpec 버전별 저장·조회(R2-030/031이 사용). */
@Slf4j
@Repository
public class FigmaScreenSpecRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FigmaScreenSpecRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @PostConstruct
    public void createTableIfNotExists() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_SCREEN_SPEC (
                SCREEN_ID              VARCHAR(64) NOT NULL,
                SCREEN_VERSION         INT NOT NULL,
                SCREEN_SPEC_ID         VARCHAR(64) NOT NULL,
                SCREEN_SPEC_VERSION    INT NOT NULL,
                SPEC_JSON              LONGTEXT NOT NULL,
                CREATED_AT             DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (SCREEN_ID, SCREEN_VERSION),
                KEY IDX_FIGMA_SCREEN_SPEC_SOURCE (SCREEN_SPEC_ID, SCREEN_SPEC_VERSION)
            )
            """);
        log.info("AI_FIGMA_SCREEN_SPEC 테이블 초기화 완료");
    }

    public void save(FigmaScreenSpec spec) {
        Optional<FigmaScreenSpec> existing = findVersion(spec.screenId(), spec.screenVersion());
        if (existing.isPresent()) {
            if (existing.get().equals(spec)) {
                return;
            }
            throw versionConflict(spec);
        }

        try {
            jdbcTemplate.update("""
                INSERT INTO AI_FIGMA_SCREEN_SPEC
                    (SCREEN_ID, SCREEN_VERSION, SCREEN_SPEC_ID, SCREEN_SPEC_VERSION, SPEC_JSON)
                VALUES (?, ?, ?, ?, ?)
                """,
                    spec.screenId(), spec.screenVersion(), spec.screenSpecificationId(),
                    spec.screenSpecificationVersion(), toJson(spec));
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            Optional<FigmaScreenSpec> concurrent = findVersion(spec.screenId(), spec.screenVersion());
            if (concurrent.isPresent() && concurrent.get().equals(spec)) {
                return;
            }
            throw versionConflict(spec);
        }
    }

    public Optional<FigmaScreenSpec> findLatest(String screenId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SPEC_JSON FROM AI_FIGMA_SCREEN_SPEC
             WHERE SCREEN_ID = ? ORDER BY SCREEN_VERSION DESC LIMIT 1
            """, String.class, screenId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public Optional<FigmaScreenSpec> findVersion(String screenId, int screenVersion) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SPEC_JSON FROM AI_FIGMA_SCREEN_SPEC
             WHERE SCREEN_ID = ? AND SCREEN_VERSION = ?
            """, String.class, screenId, screenVersion);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    /** R8-025: 화면별 최신 버전 중 지정 Design System Snapshot을 참조하는 화면을 찾는다. */
    public List<FigmaScreenSpec> findLatestByDesignSystem(
            String profileId,
            String profileVersion,
            String registryVersion
    ) {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId는 필수입니다.");
        }
        java.util.Map<String, FigmaScreenSpec> latestByScreen = new java.util.LinkedHashMap<>();
        jdbcTemplate.queryForList("""
            SELECT SPEC_JSON FROM AI_FIGMA_SCREEN_SPEC
             ORDER BY SCREEN_ID, SCREEN_VERSION DESC
            """, String.class).stream()
                .map(this::fromJson)
                .forEach(spec -> latestByScreen.putIfAbsent(spec.screenId(), spec));
        return latestByScreen.values().stream()
                .filter(spec -> spec.designSystem() != null)
                .filter(spec -> profileId.equals(spec.designSystem().profileId()))
                .filter(spec -> profileVersion == null || profileVersion.isBlank()
                        || profileVersion.equals(spec.designSystem().profileVersion()))
                .filter(spec -> registryVersion == null || registryVersion.isBlank()
                        || registryVersion.equals(spec.designSystem().registryVersion()))
                .toList();
    }

    private IllegalStateException versionConflict(FigmaScreenSpec spec) {
        return new IllegalStateException(
                "FIGMA_SCREEN_VERSION_CONFLICT: 동일한 screenId/screenVersion에 다른 내용이 이미 저장되어 있습니다: "
                        + spec.screenId() + "/" + spec.screenVersion());
    }

    private String toJson(FigmaScreenSpec spec) {
        try {
            return objectMapper.writeValueAsString(spec);
        } catch (Exception e) {
            throw new IllegalStateException("FigmaScreenSpec JSON 직렬화 실패", e);
        }
    }

    private FigmaScreenSpec fromJson(String json) {
        try {
            return objectMapper.readValue(json, FigmaScreenSpec.class);
        } catch (Exception e) {
            throw new IllegalStateException("FigmaScreenSpec JSON 역직렬화 실패", e);
        }
    }
}
