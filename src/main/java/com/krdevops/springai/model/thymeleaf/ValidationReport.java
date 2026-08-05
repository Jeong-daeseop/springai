package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;
import java.util.List;

/**
 * WP8/ARCH-0811: 한 화면에 대해 실행한 {@link ValidationGateResult} 묶음. {@code blocked}는
 * {@link GateSeverity#BLOCK} 정책의 Gate 중 하나라도 실패했는지를 나타낸다 — {@link GateSeverity#WARN}
 * Gate의 실패만으로는 {@code blocked}가 되지 않는다. {@code inputHash}는 검증 대상 HTML의 SHA-256으로,
 * 같은 입력을 재검증할 때 결과가 재현 가능한지 추적하는 데 쓴다.
 */
public record ValidationReport(
        String screenId,
        List<ValidationGateResult> results,
        boolean blocked,
        String inputHash,
        Instant createdAt
) {
    public ValidationReport {
        results = results == null ? List.of() : List.copyOf(results);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
