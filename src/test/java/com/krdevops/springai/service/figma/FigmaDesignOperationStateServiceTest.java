package com.krdevops.springai.service.figma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.ANALYZED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.APPLIED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.APPLY_REQUIRED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.CONFLICT;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.FAILED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.PREVIEW_READY;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.REJECTED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** I-1 완료 게이트: Plugin 보고 전 APPLIED 전이 불가, 허용되지 않은 상태 전이 거부. */
class FigmaDesignOperationStateServiceTest {

    private final FigmaDesignOperationStateService service = new FigmaDesignOperationStateService();

    @Test
    void happyPathFollowsAnalyzedToApplyRequired() {
        assertThatCode(() -> service.assertTransitionAllowed(ANALYZED, PREVIEW_READY))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.assertTransitionAllowed(PREVIEW_READY, APPLY_REQUIRED))
                .doesNotThrowAnyException();
    }

    @Test
    void appliedCannotBeReachedThroughGenericTransition() {
        assertThatThrownBy(() -> service.assertTransitionAllowed(APPLY_REQUIRED, APPLIED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_APPLIED_REQUIRES_PLUGIN_REPORT");
    }

    @Test
    void appliedWithoutPluginReportIsRejected() {
        assertThatThrownBy(() -> service.assertTransitionToAppliedAllowed(APPLY_REQUIRED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_APPLIED_REQUIRES_PLUGIN_REPORT");
    }

    @Test
    void appliedWithPluginReportFromApplyRequiredSucceeds() {
        assertThatCode(() -> service.assertTransitionToAppliedAllowed(APPLY_REQUIRED, true))
                .doesNotThrowAnyException();
    }

    @Test
    void appliedWithPluginReportFromAnalyzedIsStillRejected() {
        assertThatThrownBy(() -> service.assertTransitionToAppliedAllowed(ANALYZED, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_INVALID_TRANSITION");
    }

    @Test
    void conflictIsReachableFromPreviewReadyAndApplyRequired() {
        assertThatCode(() -> service.assertTransitionAllowed(PREVIEW_READY, CONFLICT))
                .doesNotThrowAnyException();
        assertThatCode(() -> service.assertTransitionAllowed(APPLY_REQUIRED, CONFLICT))
                .doesNotThrowAnyException();
    }

    @Test
    void invalidBackwardTransitionIsRejected() {
        assertThatThrownBy(() -> service.assertTransitionAllowed(APPLY_REQUIRED, ANALYZED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FIGMA_OPERATION_INVALID_TRANSITION");
    }

    @ParameterizedTest
    @EnumSource(value = com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.class,
            names = {"APPLIED", "FAILED", "REJECTED", "CONFLICT"})
    void terminalStatusesHaveNoOutgoingTransitions(
            com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus terminal) {
        assertThatCode(() -> {
            if (service.isTerminal(terminal)) {
                throw new IllegalStateException("expected-terminal");
            }
        }).hasMessageContaining("expected-terminal");
    }

    @Test
    void failedAndRejectedAreReachableFromEveryNonTerminalState() {
        for (var current : new com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus[] {
                ANALYZED, PREVIEW_READY, APPLY_REQUIRED}) {
            assertThatCode(() -> service.assertTransitionAllowed(current, FAILED))
                    .doesNotThrowAnyException();
            assertThatCode(() -> service.assertTransitionAllowed(current, REJECTED))
                    .doesNotThrowAnyException();
        }
    }
}
