package com.krdevops.springai.mapper;

import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ArtifactStatus;
import com.krdevops.springai.service.artifact.ArtifactCatalogPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ARCH-0503/0505/0506: Artifact catalog를 MySQL(AI_ARTIFACT/AI_ARTIFACT_LINK)에 저장한다.
 * CONTENT_HASH UNIQUE 제약으로 동일 내용 재저장 시 기존 레코드를 재사용한다.
 * V3 Flyway 마이그레이션 전용 신규 테이블이라 {@code @PostConstruct} DDL 안전망을 두지 않는다
 * (ARCH-0414/0415에서 확립한 패턴과 동일).
 */
@Repository
public class ArtifactCatalogRepository implements ArtifactCatalogPort {

    private static final RowMapper<Artifact> ROW_MAPPER = (rs, rowNum) -> new Artifact(
            rs.getString("ARTIFACT_ID"),
            rs.getString("ARTIFACT_TYPE"),
            rs.getString("MEDIA_TYPE"),
            rs.getLong("SIZE_BYTES"),
            rs.getString("CONTENT_HASH"),
            rs.getString("SOURCE_REVISION"),
            rs.getString("STORAGE_URI"),
            ArtifactStatus.valueOf(rs.getString("STATUS")),
            rs.getTimestamp("CREATED_AT").toInstant()
    );

    private final JdbcTemplate jdbcTemplate;

    public ArtifactCatalogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Artifact save(Artifact artifact) {
        Optional<Artifact> existing = findByContentHash(artifact.contentHash());
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            jdbcTemplate.update("""
                    INSERT INTO AI_ARTIFACT
                        (ARTIFACT_ID, ARTIFACT_TYPE, MEDIA_TYPE, SIZE_BYTES, CONTENT_HASH,
                         SOURCE_REVISION, STORAGE_URI, STATUS, CREATED_AT)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    artifact.artifactId(), artifact.artifactType(), artifact.mediaType(), artifact.sizeBytes(),
                    artifact.contentHash(), artifact.sourceRevision(), artifact.storageUri(),
                    artifact.status().name(), Timestamp.from(artifact.createdAt()));
            return artifact;
        } catch (DuplicateKeyException raceLoser) {
            return findByContentHash(artifact.contentHash()).orElseThrow(() -> raceLoser);
        }
    }

    @Override
    public Optional<Artifact> findByContentHash(String contentHash) {
        return jdbcTemplate.query("SELECT * FROM AI_ARTIFACT WHERE CONTENT_HASH = ?", ROW_MAPPER, contentHash)
                .stream().findFirst();
    }

    @Override
    public Optional<Artifact> findById(String artifactId) {
        return jdbcTemplate.query("SELECT * FROM AI_ARTIFACT WHERE ARTIFACT_ID = ?", ROW_MAPPER, artifactId)
                .stream().findFirst();
    }

    @Override
    public List<Artifact> findAll() {
        return jdbcTemplate.query("SELECT * FROM AI_ARTIFACT", ROW_MAPPER);
    }

    @Override
    public void linkToOperation(String operationId, String operationType, String artifactId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO AI_ARTIFACT_LINK (OPERATION_ID, OPERATION_TYPE, ARTIFACT_ID, LINKED_AT)
                    VALUES (?, ?, ?, ?)
                    """, operationId, operationType, artifactId, Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException alreadyLinked) {
            // 동일 Operation-Artifact 조합 재연결 — 멱등 처리하고 무시한다.
        }
    }

    @Override
    public List<Artifact> findByOperation(String operationId) {
        return jdbcTemplate.query("""
                SELECT a.* FROM AI_ARTIFACT a
                JOIN AI_ARTIFACT_LINK l ON l.ARTIFACT_ID = a.ARTIFACT_ID
                WHERE l.OPERATION_ID = ?
                """, ROW_MAPPER, operationId);
    }

    @Override
    public void updateStatus(String artifactId, ArtifactStatus status) {
        jdbcTemplate.update("UPDATE AI_ARTIFACT SET STATUS = ? WHERE ARTIFACT_ID = ?", status.name(), artifactId);
    }
}
