package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;
import java.util.List;

/**
 * I-5C: 검증 Gate 결과.
 */
public record ValidationGateResult(
    ValidationGateType gateType,
    boolean passed,
    List<String> issues,
    long durationMs,
    Instant timestamp) {

    public ValidationGateResult {
        if (issues == null) {
            issues = List.of();
        }
    }

    public boolean hasIssues() {
        return !issues.isEmpty();
    }
}
