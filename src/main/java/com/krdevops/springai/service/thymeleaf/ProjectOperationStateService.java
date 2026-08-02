package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * I-5B: Project Operation 상태 관리.
 * Operation의 상태 전이와 검증을 관리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectOperationStateService {

    /**
     * 새로운 Operation 생성.
     *
     * @param projectPath 대상 프로젝트 경로
     * @return ThymeleafProjectOperation (ANALYZED)
     */
    public ThymeleafProjectOperation createOperation(String projectPath) {
        return new ThymeleafProjectOperation(
            UUID.randomUUID().toString(),
            projectPath,
            ProjectOperationStatus.ANALYZED,
            java.util.Map.of(),
            List.of(),
            null,
            List.of(),
            List.of(),
            false,
            LocalDateTime.now(),
            null
        );
    }

    /**
     * 상태 전이 시도.
     *
     * @param operation 현재 Operation
     * @param nextStatus 목표 상태
     * @return 전이된 Operation 또는 원본 (전이 불가시)
     */
    public ThymeleafProjectOperation transitionState(
            ThymeleafProjectOperation operation,
            ProjectOperationStatus nextStatus) {

        if (!operation.canTransitionTo(nextStatus)) {
            log.warn("Invalid state transition: {} → {}", operation.status(), nextStatus);
            return operation;
        }

        log.info("State transition: {} → {} (operationId={})", 
            operation.status(), nextStatus, operation.operationId());

        return new ThymeleafProjectOperation(
            operation.operationId(),
            operation.projectPath(),
            nextStatus,
            operation.previewArtifacts(),
            operation.targetFiles(),
            operation.backupPath(),
            operation.conflictingFiles(),
            operation.validationErrors(),
            operation.readyForApply(),
            operation.createdAt(),
            operation.appliedAt()
        );
    }

    /**
     * Apply 직전 검증.
     *
     * @param operation 검증할 Operation
     * @return 검증 통과 여부
     */
    public boolean validateBeforeApply(ThymeleafProjectOperation operation) {
        if (!operation.isReadyForApply()) {
            log.error("Operation not ready for apply: {}", operation.operationId());
            return false;
        }

        if (operation.hasConflicts()) {
            log.error("Operation has conflicting files: {}", operation.conflictingFiles());
            return false;
        }

        return true;
    }

    /**
     * Apply 완료 처리.
     *
     * @param operation Apply된 Operation
     * @return VALIDATED 상태의 Operation
     */
    public ThymeleafProjectOperation markAsApplied(ThymeleafProjectOperation operation) {
        return new ThymeleafProjectOperation(
            operation.operationId(),
            operation.projectPath(),
            ProjectOperationStatus.VALIDATED,
            operation.previewArtifacts(),
            operation.targetFiles(),
            operation.backupPath(),
            operation.conflictingFiles(),
            operation.validationErrors(),
            true,
            operation.createdAt(),
            LocalDateTime.now()
        );
    }
}
