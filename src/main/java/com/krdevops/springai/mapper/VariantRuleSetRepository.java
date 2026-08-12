package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Registry 버전에 고정된 Variant Rule Set 불변 저장소. */
@Repository
public class VariantRuleSetRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public VariantRuleSetRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) return;
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_VARIANT_RULE_SET (
                RULE_SET_ID VARCHAR(64) NOT NULL,
                RULE_SET_VERSION VARCHAR(32) NOT NULL,
                PROFILE_ID VARCHAR(64) NOT NULL,
                REGISTRY_VERSION VARCHAR(32) NOT NULL,
                RULE_SET_STATUS VARCHAR(32) NOT NULL,
                RULE_SET_JSON LONGTEXT NOT NULL,
                CREATED_AT DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                PRIMARY KEY (RULE_SET_ID, RULE_SET_VERSION),
                KEY IDX_VARIANT_RULE_PROFILE (PROFILE_ID, REGISTRY_VERSION, RULE_SET_STATUS)
            )
            """);
    }

    public void saveImmutable(VariantRuleSet ruleSet) {
        Optional<VariantRuleSet> existing = findVersion(ruleSet.id(), ruleSet.version());
        if (existing.isPresent()) {
            if (existing.get().equals(ruleSet)) return;
            throw new IllegalStateException("VARIANT_RULE_SET_VERSION_CONFLICT: " + ruleSet.id() + "/" + ruleSet.version());
        }
        jdbcTemplate.update("""
            INSERT INTO AI_VARIANT_RULE_SET
                (RULE_SET_ID, RULE_SET_VERSION, PROFILE_ID, REGISTRY_VERSION, RULE_SET_STATUS, RULE_SET_JSON)
            VALUES (?, ?, ?, ?, ?, ?)
            """, ruleSet.id(), ruleSet.version(), ruleSet.profileId(), ruleSet.registryVersion(),
                ruleSet.status().name(), toJson(ruleSet));
    }

    public Optional<VariantRuleSet> findPublished(String profileId, String registryVersion) {
        List<String> values = jdbcTemplate.queryForList("""
            SELECT RULE_SET_JSON FROM AI_VARIANT_RULE_SET
             WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ? AND RULE_SET_STATUS = 'PUBLISHED'
             ORDER BY CREATED_AT DESC LIMIT 1
            """, String.class, profileId, registryVersion);
        return values.isEmpty() ? Optional.empty() : Optional.of(fromJson(values.get(0)));
    }

    public Optional<VariantRuleSet> findVersion(String id, String version) {
        List<String> values = jdbcTemplate.queryForList("""
            SELECT RULE_SET_JSON FROM AI_VARIANT_RULE_SET WHERE RULE_SET_ID = ? AND RULE_SET_VERSION = ?
            """, String.class, id, version);
        return values.isEmpty() ? Optional.empty() : Optional.of(fromJson(values.get(0)));
    }

    public VariantRuleSet transition(
            String id, String version, VariantRuleSet.Status expected, VariantRuleSet.Status target) {
        VariantRuleSet current = findVersion(id, version)
                .orElseThrow(() -> new IllegalArgumentException("Variant Rule Set 버전을 찾을 수 없습니다: " + id + "/" + version));
        if (current.status() != expected) {
            throw new IllegalStateException("VARIANT_RULE_SET_INVALID_TRANSITION: " + current.status() + " -> " + target);
        }
        VariantRuleSet changed = new VariantRuleSet(current.id(), current.version(), current.profileId(),
                current.registryVersion(), target, current.rules());
        int updated = jdbcTemplate.update("""
            UPDATE AI_VARIANT_RULE_SET SET RULE_SET_STATUS = ?, RULE_SET_JSON = ?
             WHERE RULE_SET_ID = ? AND RULE_SET_VERSION = ? AND RULE_SET_STATUS = ?
            """, target.name(), toJson(changed), id, version, expected.name());
        if (updated != 1) throw new IllegalStateException("VARIANT_RULE_SET_CONCURRENT_TRANSITION");
        return changed;
    }

    /** Spec이 기록한 정확한 버전의 Published Rule Set을 Profile/Registry 범위에서 조회한다. */
    public Optional<VariantRuleSet> findPublishedVersion(
            String profileId, String registryVersion, String ruleSetVersion) {
        List<String> values = jdbcTemplate.queryForList("""
            SELECT RULE_SET_JSON FROM AI_VARIANT_RULE_SET
             WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ? AND RULE_SET_VERSION = ?
               AND RULE_SET_STATUS = 'PUBLISHED'
             ORDER BY CREATED_AT DESC LIMIT 1
            """, String.class, profileId, registryVersion, ruleSetVersion);
        return values.isEmpty() ? Optional.empty() : Optional.of(fromJson(values.get(0)));
    }

    private String toJson(VariantRuleSet value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("VariantRuleSet JSON 직렬화 실패", exception); }
    }

    private VariantRuleSet fromJson(String value) {
        try { return objectMapper.readValue(value, VariantRuleSet.class); }
        catch (Exception exception) { throw new IllegalStateException("VariantRuleSet JSON 역직렬화 실패", exception); }
    }
}
