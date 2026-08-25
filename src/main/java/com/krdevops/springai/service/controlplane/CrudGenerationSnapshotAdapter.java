package com.krdevops.springai.service.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.controlplane.ApprovalMode;
import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationAuditRecord;
import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/** 기존 CRUD Snapshot은 성공 기준선으로만 읽고, 없는 충돌·보존 이력을 추정하지 않는다. */
@Component
public class CrudGenerationSnapshotAdapter implements GenerationOperationAdapter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CrudGenerationAuditPort auditPort;
    private final ValidationEvidencePort evidencePort;

    public CrudGenerationSnapshotAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            CrudGenerationAuditPort auditPort, ValidationEvidencePort evidencePort) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.auditPort = auditPort;
        this.evidencePort = evidencePort;
    }

    @Override
    public GenerationSourceType sourceType() {
        return GenerationSourceType.CRUD;
    }

    @Override
    public Optional<GenerationOperation> find(String operationId) {
        List<SnapshotRow> rows = jdbcTemplate.query("""
                SELECT REVISION, SNAPSHOT_JSON, CREATED_AT FROM AI_CRUD_GENERATION_SNAPSHOT
                 WHERE OPERATION_ID = ? ORDER BY REVISION DESC LIMIT 1
                """, (rs, rowNum) -> new SnapshotRow(rs.getInt("REVISION"), rs.getString("SNAPSHOT_JSON"),
                rs.getTimestamp("CREATED_AT")), operationId);
        List<GenerationAuditRecord> audits = auditPort.findAudits(operationId);
        if (rows.isEmpty() && audits.isEmpty()) return Optional.empty();

        SnapshotRow row = rows.isEmpty() ? null : rows.get(0);
        GenerationOwnershipManifest manifest = row == null ? null : manifest(row.json());
        GenerationAuditRecord latestAudit = audits.isEmpty() ? null : audits.get(audits.size() - 1);
        List<ValidationEvidence> evidence = evidencePort.findEvidence(operationId);
        List<String> changedFiles = manifest == null
                ? (latestAudit == null ? List.of() : latestAudit.changedFiles())
                : manifest.artifacts().stream().map(GenerationOwnershipManifest.ArtifactOwnership::artifactPath).toList();
        GenerationOperationStatus status = latestAudit == null
                ? GenerationOperationStatus.APPLIED : latestAudit.status();
        String auditHash = latestAudit == null ? null : ContentHashes.sha256Hex(json(latestAudit));
        return Optional.of(new GenerationOperation(operationId, GenerationSourceType.CRUD, operationId,
                "AI_CRUD_GENERATION_SNAPSHOT",
                operationId + "/" + (row == null ? "NOT_RECORDED" : row.revision()),
                latestAudit == null ? (row == null ? status.name() : "APPLIED") : status.name(),
                latestAudit == null ? null : latestAudit.projectRoot(),
                latestAudit == null ? null : latestAudit.screenId(),
                latestAudit == null ? null : latestAudit.tableName(),
                row == null ? null : String.valueOf(row.revision()),
                latestAudit == null ? (row == null ? 0 : row.revision()) : latestAudit.operationRevision(),
                ApprovalMode.AUTOMATED_OWNERSHIP_CHECK, status.name(), ProjectWritePolicy.ATOMIC_APPROVED,
                status, changedFiles,
                latestAudit == null ? List.of() : latestAudit.conflictRegionIds(),
                evidence.isEmpty() ? EvidenceRecordingStatus.NOT_RECORDED : EvidenceRecordingStatus.RECORDED,
                audits.isEmpty() ? EvidenceRecordingStatus.NOT_RECORDED : EvidenceRecordingStatus.RECORDED,
                latestAudit == null ? null : latestAudit.actorId(),
                latestAudit == null ? null : latestAudit.callerType(),
                latestAudit == null ? null : latestAudit.environment(),
                row == null ? latestAudit.createdAt() : row.createdAt().toInstant(),
                latestAudit == null ? (row == null ? null : row.createdAt().toInstant()) : latestAudit.createdAt(),
                auditHash));
    }

    @Override
    public List<ValidationEvidence> evidence(String operationId) {
        return evidencePort.findEvidence(operationId);
    }

    private GenerationOwnershipManifest manifest(String json) {
        try {
            return objectMapper.readValue(json, GenerationOwnershipManifest.class);
        } catch (Exception exception) {
            throw new IllegalStateException("CRUD 생성 Snapshot 역직렬화 실패", exception);
        }
    }

    private byte[] json(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            return value.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private record SnapshotRow(int revision, String json, Timestamp createdAt) { }
}
