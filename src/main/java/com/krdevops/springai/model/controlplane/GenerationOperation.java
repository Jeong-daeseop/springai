package com.krdevops.springai.model.controlplane;

import com.krdevops.springai.model.write.ProjectWritePolicy;

import java.time.Instant;
import java.util.List;

/** CRUD와 Thymeleaf 마이그레이션을 같은 조회 표면에서 표현하는 읽기 모델. */
public record GenerationOperation(
        String operationId,
        GenerationSourceType sourceType,
        String legacyOperationId,
        String sourceTable,
        String sourcePrimaryKey,
        String sourceStatus,
        String projectRootRef,
        String screenId,
        String tableName,
        String sourceRevision,
        int operationRevision,
        ApprovalMode approvalMode,
        String approvalState,
        ProjectWritePolicy writePolicy,
        GenerationOperationStatus status,
        List<String> changedFiles,
        List<String> conflictRefs,
        EvidenceRecordingStatus validationEvidenceStatus,
        EvidenceRecordingStatus auditRecordingStatus,
        String actorId,
        String callerType,
        String environment,
        Instant createdAt,
        Instant updatedAt,
        String auditSnapshotHash) {

    public GenerationOperation {
        changedFiles = changedFiles == null ? List.of() : List.copyOf(changedFiles);
        conflictRefs = conflictRefs == null ? List.of() : List.copyOf(conflictRefs);
        approvalMode = approvalMode == null ? ApprovalMode.UNKNOWN : approvalMode;
        status = status == null ? GenerationOperationStatus.UNKNOWN : status;
        validationEvidenceStatus = validationEvidenceStatus == null
                ? EvidenceRecordingStatus.NOT_RECORDED : validationEvidenceStatus;
        auditRecordingStatus = auditRecordingStatus == null
                ? EvidenceRecordingStatus.NOT_RECORDED : auditRecordingStatus;
    }
}
