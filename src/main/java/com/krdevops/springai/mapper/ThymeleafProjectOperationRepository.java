package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.service.thymeleaf.ThymeleafOperationStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * ARCH-0405/0409/0410/0411/0414: {@link ThymeleafOperationStore}의 MySQL Adapter.
 * {@code FigmaDesignOperationRepository}와 동일한 검증된 패턴을 그대로 따른다 —
 * {@code PRIMARY KEY(OPERATION_ID, REVISION)}가 동시 쓰기 경쟁을 DB 제약으로 차단하므로
 * 별도 분산 lock 없이 compare-and-set을 얻는다. 테이블은 {@code V2__...sql} Flyway
 * migration으로만 생성한다 — {@code @PostConstruct} DDL을 새로 추가하지 않는다(ARCH-WP3
 * 이후 신규 테이블의 목표 패턴).
 */
@Slf4j
@Repository
public class ThymeleafProjectOperationRepository implements ThymeleafOperationStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ThymeleafProjectOperationRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public Optional<ThymeleafOperationSnapshot> findLatest(String operationId) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SNAPSHOT_JSON FROM AI_THYMELEAF_PROJECT_OPERATION
             WHERE OPERATION_ID = ? ORDER BY REVISION DESC LIMIT 1
            """, String.class, operationId);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    public Optional<ThymeleafOperationSnapshot> findRevision(String operationId, int revision) {
        List<String> json = jdbcTemplate.queryForList("""
            SELECT SNAPSHOT_JSON FROM AI_THYMELEAF_PROJECT_OPERATION
             WHERE OPERATION_ID = ? AND REVISION = ?
            """, String.class, operationId, revision);
        return json.isEmpty() ? Optional.empty() : Optional.of(fromJson(json.get(0)));
    }

    /** 동일 previewHash로 재시도해도 새 Operation을 만들지 않고 기존 revision=1을 반환한다(ARCH-0411). */
    public Optional<ThymeleafOperationSnapshot> findByPreviewHash(String previewHash) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT OPERATION_ID FROM AI_THYMELEAF_PROJECT_OPERATION_IDEMPOTENCY WHERE PREVIEW_HASH = ?
            """, String.class, previewHash);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return findLatest(ids.get(0));
    }

    /** revision=1 최초 저장. previewHash가 이미 있으면 새로 만들지 않고 기존 것을 반환한다. */
    @Override
    public ThymeleafOperationSnapshot createOrReuse(ThymeleafOperationSnapshot initial) {
        if (initial.revision() != 1) {
            throw new IllegalArgumentException("createOrReuse는 revision=1만 허용합니다: " + initial.revision());
        }
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_THYMELEAF_PROJECT_OPERATION_IDEMPOTENCY (PREVIEW_HASH, OPERATION_ID)
                VALUES (?, ?)
                """, initial.previewHash(), initial.operationId());
        } catch (DuplicateKeyException exception) {
            return findByPreviewHash(initial.previewHash()).orElseThrow(() -> idempotencyIndexCorrupt(initial.previewHash()));
        }
        return save(initial);
    }

    @Override
    public ThymeleafOperationSnapshot save(ThymeleafOperationSnapshot snapshot) {
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_THYMELEAF_PROJECT_OPERATION (OPERATION_ID, REVISION, STATUS, SNAPSHOT_JSON)
                VALUES (?, ?, ?, ?)
                """, snapshot.operationId(), snapshot.revision(),
                    snapshot.operation().status().name(), toJson(snapshot));
        } catch (DuplicateKeyException exception) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_REVISION_CONFLICT: 동시 갱신으로 revision이 이미 존재합니다: "
                            + snapshot.operationId() + "/" + snapshot.revision(), exception);
        }
        return snapshot;
    }

    private IllegalStateException idempotencyIndexCorrupt(String previewHash) {
        return new IllegalStateException("THYMELEAF_OPERATION_IDEMPOTENCY_INDEX_CORRUPT: " + previewHash);
    }

    @Override
    public Optional<ThymeleafOperationSnapshot> findLatestByScreen(String projectRootHash, String screenId) {
        List<String> ids = jdbcTemplate.queryForList("""
            SELECT OPERATION_ID FROM AI_THYMELEAF_SCREEN_OPERATION_INDEX
             WHERE PROJECT_ROOT_HASH = ? AND SCREEN_ID = ?
            """, String.class, projectRootHash, screenId);
        if (ids.isEmpty()) {
            return Optional.empty();
        }
        return findLatest(ids.get(0));
    }

    @Override
    public void indexScreenOperation(String projectRootHash, String screenId, String operationId) {
        jdbcTemplate.update("""
            INSERT INTO AI_THYMELEAF_SCREEN_OPERATION_INDEX (PROJECT_ROOT_HASH, SCREEN_ID, OPERATION_ID)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE OPERATION_ID = VALUES(OPERATION_ID), UPDATED_AT = CURRENT_TIMESTAMP
            """, projectRootHash, screenId, operationId);
    }

    private String toJson(ThymeleafOperationSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception exception) {
            throw new IllegalStateException("ThymeleafOperationSnapshot JSON 직렬화 실패", exception);
        }
    }

    private ThymeleafOperationSnapshot fromJson(String json) {
        try {
            return objectMapper.readValue(json, ThymeleafOperationSnapshot.class);
        } catch (Exception exception) {
            throw new IllegalStateException("ThymeleafOperationSnapshot JSON 역직렬화 실패", exception);
        }
    }
}
