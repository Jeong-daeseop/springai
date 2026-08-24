package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.generation.CrudGenerationSnapshotStore;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link CrudGenerationSnapshotStore}의 MySQL Adapter.
 * {@code ThymeleafProjectOperationRepository}와 동일한 {@code PRIMARY KEY(OPERATION_ID, REVISION)}
 * compare-and-set 패턴을 그대로 따른다 — operationId 자체가 (outputPath, tableName, viewType)을
 * 결정적으로 인코딩하므로 별도 screen-index 테이블은 필요 없다.
 */
@Repository
public class CrudGenerationSnapshotRepository implements CrudGenerationSnapshotStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public CrudGenerationSnapshotRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Optional<GenerationOwnershipManifest> findLatest(String operationId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SNAPSHOT_JSON FROM AI_CRUD_GENERATION_SNAPSHOT
             WHERE OPERATION_ID = ? ORDER BY REVISION DESC LIMIT 1
            """, String.class, operationId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    @Override
    public void save(String operationId, GenerationOwnershipManifest manifest) {
        Integer maxRevision = jdbcTemplate.queryForObject("""
            SELECT COALESCE(MAX(REVISION), 0) FROM AI_CRUD_GENERATION_SNAPSHOT WHERE OPERATION_ID = ?
            """, Integer.class, operationId);
        int nextRevision = (maxRevision == null ? 0 : maxRevision) + 1;
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_CRUD_GENERATION_SNAPSHOT (OPERATION_ID, REVISION, SNAPSHOT_JSON)
                VALUES (?, ?, ?)
                """, operationId, nextRevision, toJson(manifest));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException(
                    "CRUD_GENERATION_SNAPSHOT_REVISION_CONFLICT: 동시 갱신으로 revision이 이미 존재합니다: "
                            + operationId + "/" + nextRevision, exception);
        }
    }

    private String toJson(GenerationOwnershipManifest manifest) {
        try {
            return objectMapper.writeValueAsString(manifest);
        } catch (Exception exception) {
            throw new IllegalStateException("GenerationOwnershipManifest JSON 직렬화 실패", exception);
        }
    }

    private GenerationOwnershipManifest fromJson(String json) {
        try {
            return objectMapper.readValue(json, GenerationOwnershipManifest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("GenerationOwnershipManifest JSON 역직렬화 실패", exception);
        }
    }
}
