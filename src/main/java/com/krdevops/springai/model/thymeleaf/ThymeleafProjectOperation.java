package com.krdevops.springai.model.thymeleaf;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * I-5B: Thymeleaf Project Operation.
 * 프로젝트에 대한 화면 전환 작업의 전체 생명주기를 추적한다.
 */
public record ThymeleafProjectOperation(
        String operationId,
        String projectPath,
        ProjectOperationStatus status,
        
        // Preview
        Map<String, String> previewArtifacts,  // viewport → preview HTML
        List<String> targetFiles,              // 대상 파일 목록
        
        // Backup & Safety
        String backupPath,
        List<String> conflictingFiles,
        
        // Validation
        List<String> validationErrors,
        boolean readyForApply,
        
        LocalDateTime createdAt,
        LocalDateTime appliedAt
) {
    public ThymeleafProjectOperation {
        previewArtifacts = previewArtifacts == null ? Map.of() : Map.copyOf(previewArtifacts);
        targetFiles = targetFiles == null ? List.of() : List.copyOf(targetFiles);
        conflictingFiles = conflictingFiles == null ? List.of() : List.copyOf(conflictingFiles);
        validationErrors = validationErrors == null ? List.of() : List.copyOf(validationErrors);
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }

    public boolean canTransitionTo(ProjectOperationStatus nextStatus) {
        return switch (status) {
            case ANALYZED -> nextStatus == ProjectOperationStatus.CONTRACT_READY;
            case CONTRACT_READY -> nextStatus == ProjectOperationStatus.PREVIEW_READY;
            case PREVIEW_READY -> nextStatus == ProjectOperationStatus.APPROVED
                    || nextStatus == ProjectOperationStatus.REJECTED
                    || nextStatus == ProjectOperationStatus.CONFLICT;
            case APPROVED -> nextStatus == ProjectOperationStatus.APPLIED
                    || nextStatus == ProjectOperationStatus.CONFLICT
                    || nextStatus == ProjectOperationStatus.FAILED;
            case APPLIED -> nextStatus == ProjectOperationStatus.VALIDATED
                    || nextStatus == ProjectOperationStatus.FAILED;
            case VALIDATED, FAILED, CONFLICT, REJECTED -> false;
        };
    }

    public boolean hasConflicts() {
        return !conflictingFiles.isEmpty();
    }

    public boolean isReadyForApply() {
        return status == ProjectOperationStatus.APPROVED
            && !hasConflicts()
            && validationErrors.isEmpty()
            && readyForApply;
    }
}
