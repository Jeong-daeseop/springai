package com.krdevops.springai.model.figma.hybrid;

import java.util.List;

/** 자동 추론 필드와 사람 확인 필드를 명시적으로 구분한다. */
public record HybridDecisionField(
        String path,
        String value,
        HybridFieldSource source,
        double confidence,
        boolean requiresHumanConfirmation,
        String reason,
        List<String> sourceNodeIds
) {
    public HybridDecisionField {
        sourceNodeIds = sourceNodeIds == null ? List.of() : List.copyOf(sourceNodeIds);
    }
}
