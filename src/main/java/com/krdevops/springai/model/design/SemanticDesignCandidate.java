package com.krdevops.springai.model.design;

import java.util.List;

/** RAG 검색 결과를 현재 분석 파이프라인과 대조한 재사용 후보. */
public record SemanticDesignCandidate(
        String analysisId,
        int rank,
        String archetype,
        String provider,
        String model,
        String promptVersion,
        boolean reusable,
        List<String> rejectionReasons
) {
    public SemanticDesignCandidate {
        rejectionReasons = rejectionReasons == null ? List.of() : List.copyOf(rejectionReasons);
    }
}
