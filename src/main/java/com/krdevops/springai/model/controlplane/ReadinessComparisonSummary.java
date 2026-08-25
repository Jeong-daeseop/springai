package com.krdevops.springai.model.controlplane;

import java.util.Map;

public record ReadinessComparisonSummary(long total, long mismatch, Map<String, Long> mismatchReasons) {
    public ReadinessComparisonSummary {
        mismatchReasons = mismatchReasons == null ? Map.of() : Map.copyOf(mismatchReasons);
    }
}
