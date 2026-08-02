package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.ANALYZED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.APPLIED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.APPROVED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.CONFLICT;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.CONTRACT_READY;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.FAILED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.PREVIEW_READY;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.REJECTED;
import static com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus.VALIDATED;

/**
 * I-5B 완료 게이트: "승인 전 대상 프로젝트 쓰기 금지"(APPROVED까지는 일반 전이, APPLIED는 실제 파일
 * 쓰기가 일어난 뒤에만), "Apply 후 재검증"(VALIDATED는 재검증 통과 뒤에만).
 */
@Service
public class ThymeleafConversionOperationStateService {

    private static final Map<ThymeleafConversionOperationStatus, Set<ThymeleafConversionOperationStatus>>
            ALLOWED_TRANSITIONS = buildTransitionGraph();

    private static Map<ThymeleafConversionOperationStatus, Set<ThymeleafConversionOperationStatus>>
            buildTransitionGraph() {
        Map<ThymeleafConversionOperationStatus, Set<ThymeleafConversionOperationStatus>> graph =
                new EnumMap<>(ThymeleafConversionOperationStatus.class);
        graph.put(ANALYZED, EnumSet.of(CONTRACT_READY, FAILED, REJECTED));
        graph.put(CONTRACT_READY, EnumSet.of(PREVIEW_READY, FAILED, REJECTED));
        graph.put(PREVIEW_READY, EnumSet.of(APPROVED, FAILED, REJECTED));
        graph.put(APPROVED, EnumSet.of(APPLIED, CONFLICT, FAILED, REJECTED));
        graph.put(APPLIED, EnumSet.of(VALIDATED, FAILED));
        graph.put(VALIDATED, EnumSet.noneOf(ThymeleafConversionOperationStatus.class));
        graph.put(FAILED, EnumSet.noneOf(ThymeleafConversionOperationStatus.class));
        graph.put(CONFLICT, EnumSet.noneOf(ThymeleafConversionOperationStatus.class));
        graph.put(REJECTED, EnumSet.noneOf(ThymeleafConversionOperationStatus.class));
        return graph;
    }

    /**
     * {@code APPLIED}/{@code VALIDATED}로의 전이는 이 메서드로 검증할 수 없다. 각각
     * {@link #assertTransitionToAppliedAllowed}/{@link #assertTransitionToValidatedAllowed}만
     * 사용할 수 있다.
     */
    public void assertTransitionAllowed(
            ThymeleafConversionOperationStatus current, ThymeleafConversionOperationStatus next) {
        if (current == null || next == null) {
            throw new IllegalArgumentException("current와 next는 필수입니다.");
        }
        if (next == APPLIED || next == VALIDATED) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_" + next + "_REQUIRES_EXPLICIT_EVIDENCE: "
                            + next + " 전이는 전용 메서드만 사용할 수 있습니다.");
        }
        Set<ThymeleafConversionOperationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_INVALID_TRANSITION: " + current + " -> " + next);
        }
    }

    /** {@code fileWritten=true}는 실제로 대상 파일에 쓰기가 끝났다는 뜻이다(승인 전 쓰기 금지 보장). */
    public void assertTransitionToAppliedAllowed(ThymeleafConversionOperationStatus current, boolean fileWritten) {
        if (!fileWritten) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_APPLIED_REQUIRES_FILE_WRITE: 실제 파일 쓰기 없이 APPLIED로 전이할 수 없습니다.");
        }
        Set<ThymeleafConversionOperationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(APPLIED)) {
            throw new IllegalStateException("THYMELEAF_OPERATION_INVALID_TRANSITION: " + current + " -> " + APPLIED);
        }
    }

    public void assertTransitionToValidatedAllowed(
            ThymeleafConversionOperationStatus current, boolean postApplyValidationPassed) {
        if (!postApplyValidationPassed) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_VALIDATED_REQUIRES_PASSED_VALIDATION: "
                            + "Apply 후 재검증을 통과하지 못하면 VALIDATED로 전이할 수 없습니다.");
        }
        Set<ThymeleafConversionOperationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(VALIDATED)) {
            throw new IllegalStateException(
                    "THYMELEAF_OPERATION_INVALID_TRANSITION: " + current + " -> " + VALIDATED);
        }
    }

    public boolean isTerminal(ThymeleafConversionOperationStatus status) {
        return ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }
}
