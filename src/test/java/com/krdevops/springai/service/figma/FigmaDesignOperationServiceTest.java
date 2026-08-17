package com.krdevops.springai.service.figma;

import com.krdevops.springai.controller.FigmaOperationsController;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.FigmaApiClient;
import com.krdevops.springai.service.FigmaApiException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * R5-042/ARCH-0821/ARCH-0822: Plugin Apply 보고 수신과 editable scope 재검증 계약.
 * 상태 규칙은 실제 {@link FigmaDesignOperationStateService}를 그대로 쓰고, Figma 접근만 대역으로 막는다.
 */
class FigmaDesignOperationServiceTest {

    private static final String OPERATION_ID = "figop-test";
    private static final String FILE_KEY = "test-file-key";

    private final FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
    private final FigmaApiClient figmaApiClient = mock(FigmaApiClient.class);
    private final FigmaDesignOperationService service = new FigmaDesignOperationService(
            repository, new FigmaDesignOperationStateService(), figmaApiClient);

    /** editableNodeIds가 없는 요청(MODIFY_EXISTING 아님)은 Figma를 조회하지 않고 그대로 APPLIED가 된다. */
    @Test
    void requestWithoutEditableNodesSkipsScopeValidation() {
        FigmaDesignOperation applyRequired = operation(
                FigmaDesignRequest.textDescription("사용자 목록", FILE_KEY),
                FigmaDesignOperationStatus.APPLY_REQUIRED);
        when(repository.findLatest(OPERATION_ID)).thenReturn(Optional.of(applyRequired));
        when(repository.transitionToApplied(eq(OPERATION_ID), anyBoolean(), anyList()))
                .thenReturn(applyRequired.withNextRevision(
                        FigmaDesignOperationStatus.APPLIED, null, List.of(), List.of()));

        FigmaDesignOperation applied = service.reportApplied(OPERATION_ID, report());

        assertThat(applied.status()).isEqualTo(FigmaDesignOperationStatus.APPLIED);
        verify(figmaApiClient, never()).findMissingNodeIds(anyString(), anyList());
    }

    /** 요청한 editable 노드가 모두 현재 파일에 남아 있으면 APPLIED로 전이한다. */
    @Test
    void allEditableNodesPresentTransitionsToApplied() {
        FigmaDesignOperation applyRequired = modifyExistingOperation(FigmaDesignOperationStatus.APPLY_REQUIRED);
        when(repository.findLatest(OPERATION_ID)).thenReturn(Optional.of(applyRequired));
        when(figmaApiClient.isFigmaEnabled()).thenReturn(true);
        when(figmaApiClient.findMissingNodeIds(FILE_KEY, List.of("1:2", "3:4"))).thenReturn(List.of());
        when(repository.transitionToApplied(eq(OPERATION_ID), anyBoolean(), anyList()))
                .thenReturn(applyRequired.withNextRevision(
                        FigmaDesignOperationStatus.APPLIED, applyRequired.sourceRevision(), List.of(), List.of()));

        FigmaDesignOperation applied = service.reportApplied(OPERATION_ID, report());

        assertThat(applied.status()).isEqualTo(FigmaDesignOperationStatus.APPLIED);
        // 노드 수만큼 왕복하지 않고 한 번의 배치 조회로 끝나야 한다.
        verify(figmaApiClient).findMissingNodeIds(FILE_KEY, List.of("1:2", "3:4"));
    }

    /** ARCH-0822: 요청 이후 노드가 사라졌으면 CONFLICT로 기록하고 APPLIED로 전이하지 않는다. */
    @Test
    void missingEditableNodeRecordsConflictAndBlocksApply() {
        FigmaDesignOperation applyRequired = modifyExistingOperation(FigmaDesignOperationStatus.APPLY_REQUIRED);
        when(repository.findLatest(OPERATION_ID)).thenReturn(Optional.of(applyRequired));
        when(figmaApiClient.isFigmaEnabled()).thenReturn(true);
        when(figmaApiClient.findMissingNodeIds(FILE_KEY, List.of("1:2", "3:4"))).thenReturn(List.of("3:4"));

        assertThatThrownBy(() -> service.reportApplied(OPERATION_ID, report()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_EDITABLE_SCOPE_CONFLICT")
                .hasMessageContaining("3:4");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GenerationIssue>> issues = ArgumentCaptor.forClass(List.class);
        verify(repository).appendTransition(eq(OPERATION_ID), eq(FigmaDesignOperationStatus.CONFLICT),
                eq(applyRequired.sourceRevision()), issues.capture(), anyList());
        assertThat(issues.getValue()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("FIGMA_EDITABLE_NODE_MISSING");
            assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.ERROR);
            assertThat(issue.sourceLocation()).isEqualTo("3:4");
        });
        verify(repository, never()).transitionToApplied(anyString(), anyBoolean(), anyList());
    }

    /**
     * R5-042: 요청 노드는 모두 살아 있어도(findMissingNodeIds 통과), Plugin이 실제로 보고한
     * affectedNodeIds가 승인된 editableNodeIds 범위를 벗어나면 CONFLICT로 막아야 한다.
     */
    @Test
    void affectedNodeOutsideEditableScopeRecordsConflictAndBlocksApply() {
        FigmaDesignOperation applyRequired = modifyExistingOperation(FigmaDesignOperationStatus.APPLY_REQUIRED);
        when(repository.findLatest(OPERATION_ID)).thenReturn(Optional.of(applyRequired));
        when(figmaApiClient.isFigmaEnabled()).thenReturn(true);
        when(figmaApiClient.findMissingNodeIds(FILE_KEY, List.of("1:2", "3:4"))).thenReturn(List.of());
        FigmaOperationsController.PluginApplyReport outOfScopeReport = new FigmaOperationsController.PluginApplyReport(
                "user-list", List.of("1:2", "9:9"), 2, 0, 0, "적용 완료");

        assertThatThrownBy(() -> service.reportApplied(OPERATION_ID, outOfScopeReport))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_EDITABLE_SCOPE_CONFLICT")
                .hasMessageContaining("9:9");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GenerationIssue>> issues = ArgumentCaptor.forClass(List.class);
        verify(repository).appendTransition(eq(OPERATION_ID), eq(FigmaDesignOperationStatus.CONFLICT),
                eq(applyRequired.sourceRevision()), issues.capture(), anyList());
        assertThat(issues.getValue()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("FIGMA_EDITABLE_NODE_OUT_OF_SCOPE");
            assertThat(issue.severity()).isEqualTo(GenerationIssue.Severity.ERROR);
            assertThat(issue.sourceLocation()).isEqualTo("9:9");
        });
        verify(repository, never()).transitionToApplied(anyString(), anyBoolean(), anyList());
    }

    /**
     * Figma 연동이 꺼져 있으면(기본값) 검증할 수단이 없다. 여기서 실패시키면 MODIFY_EXISTING
     * Operation이 APPLY_REQUIRED에 갇혀 APPLIED로 영영 도달하지 못하므로 검증을 건너뛰어야 한다.
     */
    @Test
    void figmaDisabledSkipsScopeValidationInsteadOfTrappingOperation() {
        FigmaDesignOperation applyRequired = modifyExistingOperation(FigmaDesignOperationStatus.APPLY_REQUIRED);
        when(repository.findLatest(OPERATION_ID)).thenReturn(Optional.of(applyRequired));
        when(figmaApiClient.isFigmaEnabled()).thenReturn(false);
        when(repository.transitionToApplied(eq(OPERATION_ID), anyBoolean(), anyList()))
                .thenReturn(applyRequired.withNextRevision(
                        FigmaDesignOperationStatus.APPLIED, applyRequired.sourceRevision(), List.of(), List.of()));

        FigmaDesignOperation applied = service.reportApplied(OPERATION_ID, report());

        assertThat(applied.status()).isEqualTo(FigmaDesignOperationStatus.APPLIED);
        verify(figmaApiClient, never()).findMissingNodeIds(anyString(), anyList());
        verify(repository, never()).appendTransition(anyString(), any(), any(), anyList(), anyList());
    }

    /** 인증 실패 같은 다른 Figma 오류는 스코프 충돌로 오판하지 않고 그대로 전파한다. */
    @Test
    void nonNotFoundFigmaErrorPropagatesWithoutConflict() {
        when(repository.findLatest(OPERATION_ID))
                .thenReturn(Optional.of(modifyExistingOperation(FigmaDesignOperationStatus.APPLY_REQUIRED)));
        when(figmaApiClient.isFigmaEnabled()).thenReturn(true);
        when(figmaApiClient.findMissingNodeIds(anyString(), anyList()))
                .thenThrow(new FigmaApiException("FIGMA_AUTH_FAILED", 401, "Figma 인증에 실패했습니다."));

        assertThatThrownBy(() -> service.reportApplied(OPERATION_ID, report()))
                .isInstanceOf(FigmaApiException.class)
                .extracting(exception -> ((FigmaApiException) exception).code())
                .isEqualTo("FIGMA_AUTH_FAILED");

        verify(repository, never()).appendTransition(anyString(), any(), any(), anyList(), anyList());
        verify(repository, never()).transitionToApplied(anyString(), anyBoolean(), anyList());
    }

    /**
     * ARCH-0821: Plugin Apply 보고서가 오기 전(=아직 APPLY_REQUIRED가 아닌) Operation은 APPLIED로
     * 전이할 수 없고, APPLY_REQUIRED로 올라온 뒤에야 같은 호출이 성공한다.
     */
    @Test
    void appliedIsBlockedUntilOperationReachesApplyRequired() {
        FigmaDesignRequest request = FigmaDesignRequest.textDescription("사용자 목록", FILE_KEY);
        when(repository.findLatest(OPERATION_ID))
                .thenReturn(Optional.of(operation(request, FigmaDesignOperationStatus.PREVIEW_READY)));

        assertThatThrownBy(() -> service.reportApplied(OPERATION_ID, report()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_INVALID_TRANSITION");
        verify(repository, never()).transitionToApplied(anyString(), anyBoolean(), anyList());

        FigmaDesignOperation applyRequired = operation(request, FigmaDesignOperationStatus.APPLY_REQUIRED);
        when(repository.findLatest(OPERATION_ID)).thenReturn(Optional.of(applyRequired));
        when(repository.transitionToApplied(eq(OPERATION_ID), anyBoolean(), anyList()))
                .thenReturn(applyRequired.withNextRevision(
                        FigmaDesignOperationStatus.APPLIED, null, List.of(), List.of()));

        assertThat(service.reportApplied(OPERATION_ID, report()).status())
                .isEqualTo(FigmaDesignOperationStatus.APPLIED);
        verify(repository).transitionToApplied(OPERATION_ID, true, List.of());
    }

    private FigmaOperationsController.PluginApplyReport report() {
        return new FigmaOperationsController.PluginApplyReport(
                "user-list", List.of("1:2"), 1, 0, 0, "적용 완료");
    }

    private FigmaDesignOperation modifyExistingOperation(FigmaDesignOperationStatus status) {
        return operation(FigmaDesignRequest.modifyExisting(
                "목록 화면 수정", FILE_KEY, List.of("1:2", "3:4")), status);
    }

    private FigmaDesignOperation operation(FigmaDesignRequest request, FigmaDesignOperationStatus status) {
        Instant now = Instant.now();
        return new FigmaDesignOperation(
                OPERATION_ID, 2, request, "b".repeat(64), status,
                new SourceRevisionRef(FILE_KEY, "spec-1:" + "c".repeat(64), now),
                List.of(), List.of(), now, now);
    }
}
