package com.krdevops.springai.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.controlplane.GenerationAuditRecord;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.service.controlplane.CrudGenerationAuditPort;
import com.krdevops.springai.service.controlplane.ValidationEvidencePort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 신규 실행부터 생성 판정과 검증 증적을 저장하는 공통 제어 계층 저장소. */
@Repository
public class GenerationControlPlaneRepository implements CrudGenerationAuditPort, ValidationEvidencePort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public GenerationControlPlaneRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(GenerationAuditRecord record) {
        int revision = record.operationRevision() > 0 ? record.operationRevision() : nextAuditRevision(record.operationId());
        jdbcTemplate.update("""
                INSERT INTO AI_GENERATION_OPERATION_AUDIT
                (AUDIT_ID, OPERATION_ID, OPERATION_REVISION, PROJECT_ROOT, TABLE_NAME, SCREEN_ID,
                 CALLER_TYPE, ACTOR_ID, ENVIRONMENT_NAME, CHANGED_REGIONS_JSON,
                 PRESERVED_REGIONS_JSON, CONFLICT_REGIONS_JSON, CHANGED_FILES_JSON, STATUS,
                 FAILURE_STAGE, FAILURE_DETAIL, CREATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, record.auditId(), record.operationId(), revision, record.projectRoot(), record.tableName(),
                record.screenId(), record.callerType(), record.actorId(), record.environment(),
                json(record.changedRegionIds()), json(record.preservedRegionIds()), json(record.conflictRegionIds()),
                json(record.changedFiles()), record.status().name(), record.failureStage(), record.failureDetail(),
                Timestamp.from(record.createdAt()));
    }

    @Override
    public List<GenerationAuditRecord> findAudits(String operationId) {
        return jdbcTemplate.query("""
                SELECT * FROM AI_GENERATION_OPERATION_AUDIT
                 WHERE OPERATION_ID = ? ORDER BY OPERATION_REVISION, CREATED_AT, AUDIT_ID
                """, (rs, rowNum) -> new GenerationAuditRecord(
                rs.getString("AUDIT_ID"), rs.getString("OPERATION_ID"), rs.getInt("OPERATION_REVISION"),
                rs.getString("PROJECT_ROOT"), rs.getString("TABLE_NAME"), rs.getString("SCREEN_ID"),
                rs.getString("CALLER_TYPE"), rs.getString("ACTOR_ID"), rs.getString("ENVIRONMENT_NAME"),
                strings(rs.getString("CHANGED_REGIONS_JSON")),
                strings(rs.getString("PRESERVED_REGIONS_JSON")),
                strings(rs.getString("CONFLICT_REGIONS_JSON")), strings(rs.getString("CHANGED_FILES_JSON")),
                GenerationOperationStatus.valueOf(rs.getString("STATUS")), rs.getString("FAILURE_STAGE"),
                rs.getString("FAILURE_DETAIL"), rs.getTimestamp("CREATED_AT").toInstant()), operationId);
    }

    @Override
    public void append(ValidationEvidence evidence) {
        jdbcTemplate.update("""
                INSERT INTO AI_GENERATION_VALIDATION_EVIDENCE
                (EVIDENCE_ID, OPERATION_ID, GATE_TYPE, STATUS, SEVERITY, INPUT_REFS_JSON,
                 OUTPUT_REFS_JSON, CONTENT_HASH, SOURCE_REVISION, VALIDATOR_VERSION, CREATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, evidence.evidenceId(), evidence.operationId(), evidence.gateType().name(),
                evidence.status().name(), evidence.severity().name(), json(evidence.inputRefs()),
                json(evidence.outputRefs()), evidence.contentHash(), evidence.sourceRevision(),
                evidence.validatorVersion(), Timestamp.from(evidence.createdAt()));
    }

    @Override
    public List<ValidationEvidence> findEvidence(String operationId) {
        return jdbcTemplate.query("""
                SELECT * FROM AI_GENERATION_VALIDATION_EVIDENCE
                 WHERE OPERATION_ID = ? ORDER BY CREATED_AT, EVIDENCE_ID
                """, (rs, rowNum) -> new ValidationEvidence(
                rs.getString("EVIDENCE_ID"), rs.getString("OPERATION_ID"),
                ValidationEvidence.GateType.valueOf(rs.getString("GATE_TYPE")),
                ValidationEvidence.Status.valueOf(rs.getString("STATUS")),
                ValidationEvidence.Severity.valueOf(rs.getString("SEVERITY")),
                strings(rs.getString("INPUT_REFS_JSON")), strings(rs.getString("OUTPUT_REFS_JSON")),
                rs.getString("CONTENT_HASH"), rs.getString("SOURCE_REVISION"),
                rs.getString("VALIDATOR_VERSION"), rs.getTimestamp("CREATED_AT").toInstant()), operationId);
    }

    private int nextAuditRevision(String operationId) {
        Integer latest = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(OPERATION_REVISION), 0)
                  FROM AI_GENERATION_OPERATION_AUDIT WHERE OPERATION_ID = ?
                """, Integer.class, operationId);
        return (latest == null ? 0 : latest) + 1;
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception exception) {
            throw new IllegalStateException("공통 제어 계층 JSON 직렬화 실패", exception);
        }
    }

    private List<String> strings(String value) {
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (Exception exception) {
            throw new IllegalStateException("공통 제어 계층 JSON 역직렬화 실패", exception);
        }
    }
}
