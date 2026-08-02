package com.krdevops.springai.service.thymeleaf;

import org.junit.jupiter.api.Test;

import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.ANALYZED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.APPLIED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.APPROVED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.CONFLICT;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.CONTRACT_READY;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.FAILED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.PREVIEW_READY;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.REJECTED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.VALIDATED;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** I-5B 완료 게이트: 승인 전 파일 쓰기 금지, Apply 후 재검증 통과 전 VALIDATED 금지. */
class ThymeleafConversionOperationStateServiceTest {

    private final ThymeleafConversionOperationStateService service = new ThymeleafConversionOperationStateService();

    @Test
    void happyPathFollowsFullPipeline() {
        assertThatCode(() -> service.assertTransitionAllowed(ANALYZED, CONTRACT_READY)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertTransitionAllowed(CONTRACT_READY, PREVIEW_READY)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertTransitionAllowed(PREVIEW_READY, APPROVED)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertTransitionToAppliedAllowed(APPROVED, true)).doesNotThrowAnyException();
        assertThatCode(() -> service.assertTransitionToValidatedAllowed(APPLIED, true)).doesNotThrowAnyException();
    }

    @Test
    void appliedCannotBeReachedThroughGenericTransition() {
        assertThatThrownBy(() -> service.assertTransitionAllowed(APPROVED, APPLIED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_OPERATION_APPLIED_REQUIRES_EXPLICIT_EVIDENCE");
    }

    @Test
    void appliedWithoutFileWriteIsRejected() {
        assertThatThrownBy(() -> service.assertTransitionToAppliedAllowed(APPROVED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_OPERATION_APPLIED_REQUIRES_FILE_WRITE");
    }

    @Test
    void appliedFromNonApprovedIsRejectedEvenWithFileWritten() {
        assertThatThrownBy(() -> service.assertTransitionToAppliedAllowed(PREVIEW_READY, true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_OPERATION_INVALID_TRANSITION");
    }

    @Test
    void validatedWithoutPassedValidationIsRejected() {
        assertThatThrownBy(() -> service.assertTransitionToValidatedAllowed(APPLIED, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_OPERATION_VALIDATED_REQUIRES_PASSED_VALIDATION");
    }

    @Test
    void conflictIsReachableOnlyFromApproved() {
        assertThatCode(() -> service.assertTransitionAllowed(APPROVED, CONFLICT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> service.assertTransitionAllowed(ANALYZED, CONFLICT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failedAndRejectedAreReachableFromEveryPreApprovalState() {
        for (var current : new com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus[] {
                ANALYZED, CONTRACT_READY, PREVIEW_READY, APPROVED}) {
            assertThatCode(() -> service.assertTransitionAllowed(current, FAILED)).doesNotThrowAnyException();
        }
        for (var current : new com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus[] {
                ANALYZED, CONTRACT_READY, PREVIEW_READY, APPROVED}) {
            assertThatCode(() -> service.assertTransitionAllowed(current, REJECTED)).doesNotThrowAnyException();
        }
    }

    @Test
    void terminalStatusesAreIdentifiedCorrectly() {
        for (var terminal : new com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus[] {
                VALIDATED, FAILED, CONFLICT, REJECTED}) {
            assertThatCode(() -> {
                if (!service.isTerminal(terminal)) {
                    throw new IllegalStateException("expected-terminal:" + terminal);
                }
            }).doesNotThrowAnyException();
        }
        assertThatCode(() -> {
            if (service.isTerminal(ANALYZED)) {
                throw new IllegalStateException("unexpected-terminal");
            }
        }).doesNotThrowAnyException();
    }
}
