package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.krdevops.springai.config.StubEmbeddingModelTestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CI 러너에는 로컬 전용 ko-sroberta ONNX 모델 파일도, Redis 서버도 없어 이 테스트가 필요로 하지
 * 않는 실제 임베딩/VectorStore auto-config가 각각 파일 I/O와 연결 단계에서 실패한다.
 * spring.ai.model.embedding=none + spring.ai.vectorstore.redis.enabled=false(TestPropertySource)로
 * 끄고 StubEmbeddingModelTestConfig의 no-op Bean으로 대체한다 — 전자만으로는 부족하다.
 * TransformersEmbeddingModelAutoConfiguration의 @ConditionalOnMissingBean이 구현 클래스
 * (TransformersEmbeddingModel) 기준이라 인터페이스 스텁 Bean만으로는 비활성화되지 않는다.
 */
@SpringBootTest
@TestPropertySource(properties = {"spring.ai.model.embedding=none", "spring.ai.vectorstore.redis.enabled=false"})
@Import(StubEmbeddingModelTestConfig.class)
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
