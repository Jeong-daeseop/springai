package com.krdevops.springai.model.design;

/** 동일 캐시 키 저장 경쟁에서 확정된 결과와 현재 호출자의 삽입 여부. */
public record DesignAnalysisSaveOutcome(
        DesignAnalysisResult result,
        boolean insertedByCaller
) {
    public DesignAnalysisSaveOutcome {
        if (result == null) throw new IllegalArgumentException("확정된 디자인 분석 결과가 필요합니다.");
    }
}
