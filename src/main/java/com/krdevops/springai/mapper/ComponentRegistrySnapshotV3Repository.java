package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Catalog SSOT에 종속된 Registry v3 Binding Snapshot의 불변 저장소. */
@Repository
public class ComponentRegistrySnapshotV3Repository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ComponentRegistrySnapshotV3Repository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void saveImmutable(ComponentRegistrySnapshotV3 snapshot) {
        jdbcTemplate.update("""
            INSERT INTO AI_COMPONENT_REGISTRY_V3
              (PROFILE_ID, REGISTRY_VERSION, CATALOG_VERSION, SCHEMA_VERSION,
               SOURCE_REVISION, APPROVED_BY, APPROVED_AT, CONTENT_HASH, REGISTRY_JSON)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, snapshot.profileId(), snapshot.registryVersion(), snapshot.catalogVersion(),
                snapshot.schemaVersion(), snapshot.sourceRevision(), snapshot.approvedBy(),
                snapshot.approvedAt(), snapshot.contentHash(), toJson(snapshot));
    }

    public Optional<ComponentRegistrySnapshotV3> findVersion(String profileId, String registryVersion) {
        List<String> rows = jdbcTemplate.queryForList("""
            SELECT REGISTRY_JSON FROM AI_COMPONENT_REGISTRY_V3
             WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ?
            """, String.class, profileId, registryVersion);
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromJson(rows.get(0)));
    }

    public Optional<ComponentRegistrySnapshotV3> findLatestApproved(String profileId) {
        List<String> rows = jdbcTemplate.queryForList("""
            SELECT REGISTRY_JSON FROM AI_COMPONENT_REGISTRY_V3
             WHERE PROFILE_ID = ? AND APPROVED_AT IS NOT NULL
             ORDER BY APPROVED_AT DESC, CREATED_AT DESC LIMIT 1
            """, String.class, profileId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(fromJson(rows.get(0)));
    }

    public List<ComponentRegistrySnapshotV3> findAllApproved() {
        return jdbcTemplate.queryForList("""
            SELECT REGISTRY_JSON FROM AI_COMPONENT_REGISTRY_V3
             WHERE APPROVED_AT IS NOT NULL
             ORDER BY PROFILE_ID, APPROVED_AT, CREATED_AT
            """, String.class).stream().map(this::fromJson).toList();
    }

    private String toJson(ComponentRegistrySnapshotV3 snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalStateException("Registry v3 JSON 직렬화 실패", e);
        }
    }

    private ComponentRegistrySnapshotV3 fromJson(String json) {
        try {
            return objectMapper.readValue(json, ComponentRegistrySnapshotV3.class);
        } catch (Exception e) {
            throw new IllegalStateException("Registry v3 JSON 역직렬화 실패", e);
        }
    }
}
