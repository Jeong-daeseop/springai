package com.krdevops.springai.model.design;

import java.util.List;

/**
 * Figma 원본 분석 결과와, 생성된 화면을 재캡처해 분석한 결과 사이의 구조적 일치도.
 * 픽셀 이미지 비교가 아니라 archetype/컴포넌트 타입/필드 역할/액션 타입 집합의 Jaccard
 * 유사도이며, 시각적(색상·정확한 좌표) 일치를 보장하지 않는다 —
 * Figma_픽셀재현_제외범위_구현계획.md 트랙 A 참고.
 */
public record DesignFidelityReport(
        String originalAnalysisId,
        String renderedAnalysisId,
        double archetypeMatch,
        double componentOverlapRatio,
        double fieldRoleOverlapRatio,
        double actionOverlapRatio,
        List<String> missingInRendered,
        List<String> extraInRendered
) {
    public DesignFidelityReport {
        missingInRendered = missingInRendered == null ? List.of() : List.copyOf(missingInRendered);
        extraInRendered = extraInRendered == null ? List.of() : List.copyOf(extraInRendered);
    }
}
