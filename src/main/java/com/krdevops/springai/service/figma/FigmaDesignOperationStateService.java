package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.ANALYZED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.APPLIED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.APPLY_REQUIRED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.CONFLICT;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.FAILED;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.PREVIEW_READY;
import static com.krdevops.springai.model.figma.request.FigmaDesignOperationStatus.REJECTED;

/**
 * I-1 완료 게이트: "Plugin 보고서 전 APPLIED 전이 불가", "source revision 불일치 시 CONFLICT".
 * {@link com.krdevops.springai.mapper.FigmaDesignOperationRepository}가 새 revision을 저장하기
 * 전에 이 서비스로 전이 허용 여부를 검증한다. 순수 상태 규칙만 다루며 저장·해시 계산은 하지 않는다.
 */
@Service
public class FigmaDesignOperationStateService {

    private static final Map<FigmaDesignOperationStatus, Set<FigmaDesignOperationStatus>> ALLOWED_TRANSITIONS =
            buildTransitionGraph();

    private static Map<FigmaDesignOperationStatus, Set<FigmaDesignOperationStatus>> buildTransitionGraph() {
        Map<FigmaDesignOperationStatus, Set<FigmaDesignOperationStatus>> graph =
                new EnumMap<>(FigmaDesignOperationStatus.class);
        graph.put(ANALYZED, EnumSet.of(PREVIEW_READY, FAILED, REJECTED));
        graph.put(PREVIEW_READY, EnumSet.of(APPLY_REQUIRED, FAILED, REJECTED, CONFLICT));
        graph.put(APPLY_REQUIRED, EnumSet.of(APPLIED, FAILED, REJECTED, CONFLICT));
        graph.put(APPLIED, EnumSet.noneOf(FigmaDesignOperationStatus.class));
        graph.put(FAILED, EnumSet.noneOf(FigmaDesignOperationStatus.class));
        graph.put(REJECTED, EnumSet.noneOf(FigmaDesignOperationStatus.class));
        graph.put(CONFLICT, EnumSet.noneOf(FigmaDesignOperationStatus.class));
        return graph;
    }

    /**
     * {@code APPLIED}로의 전이는 이 메서드로 검증할 수 없다. 반드시
     * {@link #assertTransitionToAppliedAllowed(FigmaDesignOperationStatus, boolean)}를 사용해
     * Plugin 적용 보고 여부를 명시적으로 전달해야 한다.
     */
    public void assertTransitionAllowed(FigmaDesignOperationStatus current, FigmaDesignOperationStatus next) {
        if (current == null || next == null) {
            throw new IllegalArgumentException("current와 next는 필수입니다.");
        }
        if (next == APPLIED) {
            throw new IllegalStateException(
                    "FIGMA_OPERATION_APPLIED_REQUIRES_PLUGIN_REPORT: "
                            + "APPLIED 전이는 assertTransitionToAppliedAllowed(...)만 사용할 수 있습니다.");
        }
        Set<FigmaDesignOperationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException(
                    "FIGMA_OPERATION_INVALID_TRANSITION: " + current + " -> " + next);
        }
    }

    public void assertTransitionToAppliedAllowed(FigmaDesignOperationStatus current, boolean pluginReportReceived) {
        if (!pluginReportReceived) {
            throw new IllegalStateException(
                    "FIGMA_OPERATION_APPLIED_REQUIRES_PLUGIN_REPORT: "
                            + "실제 Plugin 적용 보고 없이 APPLIED로 전이할 수 없습니다.");
        }
        Set<FigmaDesignOperationStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(APPLIED)) {
            throw new IllegalStateException(
                    "FIGMA_OPERATION_INVALID_TRANSITION: " + current + " -> " + APPLIED);
        }
    }

    public boolean isTerminal(FigmaDesignOperationStatus status) {
        return ALLOWED_TRANSITIONS.getOrDefault(status, Set.of()).isEmpty();
    }
}
