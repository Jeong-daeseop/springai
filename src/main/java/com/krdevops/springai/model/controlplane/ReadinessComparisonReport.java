package com.krdevops.springai.model.controlplane;

import java.time.Instant;
import java.util.Map;

/** 지정 기간의 Readiness 병행 관측 분석 결과. */
public record ReadinessComparisonReport(
        Instant from,
        Instant to,
        long total,
        long mismatch,
        double mismatchRate,
        Map<String, Long> mismatchReasons,
        Map<String, Long> sourceTypeCounts,
        Map<String, Long> dailyCounts) {

    public ReadinessComparisonReport {
        mismatchReasons = mismatchReasons == null ? Map.of() : Map.copyOf(mismatchReasons);
        sourceTypeCounts = sourceTypeCounts == null ? Map.of() : Map.copyOf(sourceTypeCounts);
        dailyCounts = dailyCounts == null ? Map.of() : Map.copyOf(dailyCounts);
    }
}
