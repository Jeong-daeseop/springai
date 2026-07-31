package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** R1-021: Publish 후 동기화된 ComponentRegistry의 버전별 저장·조회(R4). */
@Slf4j
@Repository
public class ComponentRegistryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ComponentRegistryRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @PostConstruct
    public void createTableIfNotExists() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_COMPONENT_REGISTRY (
                PROFILE_ID       VARCHAR(64) NOT NULL,
                REGISTRY_VERSION VARCHAR(32) NOT NULL,
                REGISTRY_JSON    LONGTEXT NOT NULL,
                CREATED_AT       DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                PRIMARY KEY (PROFILE_ID, REGISTRY_VERSION)
            )
            """);
        log.info("AI_COMPONENT_REGISTRY 테이블 초기화 완료");
    }

    public void save(ComponentRegistry registry) {
        jdbcTemplate.update("""
            INSERT INTO AI_COMPONENT_REGISTRY (PROFILE_ID, REGISTRY_VERSION, REGISTRY_JSON)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE REGISTRY_JSON = VALUES(REGISTRY_JSON)
            """, registry.profileId(), registry.registryVersion(), toJson(registry));
    }

    /** R4 Snapshot은 동일 version을 덮어쓰지 않는다. 중복이면 DataAccessException으로 실패한다. */
    public void saveImmutable(ComponentRegistry registry) {
        jdbcTemplate.update("""
            INSERT INTO AI_COMPONENT_REGISTRY (PROFILE_ID, REGISTRY_VERSION, REGISTRY_JSON)
            VALUES (?, ?, ?)
            """, registry.profileId(), registry.registryVersion(), toJson(registry));
    }

    public Optional<ComponentRegistry> findLatest(String profileId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT REGISTRY_JSON FROM AI_COMPONENT_REGISTRY
             WHERE PROFILE_ID = ? ORDER BY CREATED_AT DESC LIMIT 1
            """, String.class, profileId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public Optional<ComponentRegistry> findVersion(String profileId, String registryVersion) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT REGISTRY_JSON FROM AI_COMPONENT_REGISTRY
             WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ?
            """, String.class, profileId, registryVersion);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    private String toJson(ComponentRegistry registry) {
        try {
            return objectMapper.writeValueAsString(registry);
        } catch (Exception e) {
            throw new IllegalStateException("ComponentRegistry JSON 직렬화 실패", e);
        }
    }

    private ComponentRegistry fromJson(String json) {
        try {
            return objectMapper.readValue(json, ComponentRegistry.class);
        } catch (Exception e) {
            throw new IllegalStateException("ComponentRegistry JSON 역직렬화 실패", e);
        }
    }
}
