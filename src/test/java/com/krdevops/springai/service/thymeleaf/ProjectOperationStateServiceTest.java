package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("I-5B: ProjectOperationStateService 테스트")
class ProjectOperationStateServiceTest {

    @Autowired
    private ProjectOperationStateService stateService;

    @Test
    @DisplayName("새로운 Operation 생성")
    void testCreateOperation() {
        var op = stateService.createOperation("/project/path");

        assertNotNull(op);
        assertEquals(ProjectOperationStatus.ANALYZED, op.status());
        assertEquals("/project/path", op.projectPath());
    }

    @Test
    @DisplayName("상태 전이: ANALYZED → CONTRACT_READY")
    void testStateTransition() {
        var op = stateService.createOperation("/project");
        var nextOp = stateService.transitionState(op, ProjectOperationStatus.CONTRACT_READY);

        assertEquals(ProjectOperationStatus.CONTRACT_READY, nextOp.status());
    }

    @Test
    @DisplayName("유효하지 않은 상태 전이 차단")
    void testInvalidStateTransitionBlocked() {
        var op = stateService.createOperation("/project");
        var nextOp = stateService.transitionState(op, ProjectOperationStatus.APPLIED);

        // 직접 APPLIED로 전이 불가 → 원본 상태 유지
        assertEquals(ProjectOperationStatus.ANALYZED, nextOp.status());
    }

    @Test
    @DisplayName("Apply 준비 상태 검증")
    void testValidateBeforeApply() {
        var op = stateService.createOperation("/project");

        // 초기 상태에서는 Apply 불가
        assertFalse(stateService.validateBeforeApply(op));
    }

    @Test
    @DisplayName("승인된 Operation은 APPLIED 후 VALIDATED로 전이")
    void testMarkAsApplied() {
        var created = stateService.createOperation("/project");
        var contractReady = stateService.transitionState(created, ProjectOperationStatus.CONTRACT_READY);
        var previewReady = stateService.transitionState(contractReady, ProjectOperationStatus.PREVIEW_READY);
        var approvedBase = stateService.transitionState(previewReady, ProjectOperationStatus.APPROVED);
        var approved = new com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation(
                approvedBase.operationId(), approvedBase.projectPath(), approvedBase.status(),
                approvedBase.previewArtifacts(), approvedBase.targetFiles(), approvedBase.backupPath(),
                approvedBase.conflictingFiles(), approvedBase.validationErrors(), true,
                approvedBase.createdAt(), approvedBase.appliedAt());

        var applied = stateService.markAsApplied(approved);
        assertEquals(ProjectOperationStatus.APPLIED, applied.status());
        assertNotNull(applied.appliedAt());

        var validated = stateService.markAsValidated(applied);
        assertEquals(ProjectOperationStatus.VALIDATED, validated.status());
    }

    @Test
    @DisplayName("승인되지 않은 Operation은 Apply 불가")
    void testMarkAsAppliedRejectsUnapprovedOperation() {
        var operation = stateService.createOperation("/project");

        assertThrows(IllegalStateException.class, () -> stateService.markAsApplied(operation));
    }
}
