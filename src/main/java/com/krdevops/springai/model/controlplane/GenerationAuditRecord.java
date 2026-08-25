package com.krdevops.springai.model.controlplane;

import java.time.Instant;
import java.util.List;

/** 한 번의 CRUD 생성 시도에서 계산된 변경·보존·충돌 결과. 성공 Snapshot과 별도로 남긴다. */
public record GenerationAuditRecord(
        String auditId,
        String operationId,
        int operationRevision,
        String projectRoot,
        String tableName,
        String screenId,
        String callerType,
        String actorId,
        String environment,
        List<String> changedRegionIds,
        List<String> preservedRegionIds,
        List<String> conflictRegionIds,
        List<String> changedFiles,
        GenerationOperationStatus status,
        String failureStage,
        String failureDetail,
        Instant createdAt) {

    public GenerationAuditRecord {
        changedRegionIds = copy(changedRegionIds);
        preservedRegionIds = copy(preservedRegionIds);
        conflictRegionIds = copy(conflictRegionIds);
        changedFiles = copy(changedFiles);
        status = status == null ? GenerationOperationStatus.UNKNOWN : status;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
