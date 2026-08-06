package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** WP8 ARCH-0808~0810의 viewport별 증적과 공통 Gate 결과. */
public record BrowserValidationReport(
        String schemaVersion,
        String screenId,
        boolean blocked,
        Instant createdAt,
        BrowserEngine browser,
        String reportPath,
        List<BrowserViewportResult> viewports
) {
    public BrowserValidationReport {
        createdAt = createdAt == null ? Instant.now() : createdAt;
        viewports = viewports == null ? List.of() : List.copyOf(viewports);
    }

    public record BrowserEngine(String engine, String version) { }

    /** 기존 {@link ValidationReport}에 합칠 수 있는 3개 공통 Gate 결과로 축약한다. */
    public List<ValidationGateResult> toGateResults() {
        return List.of(
                aggregate(ValidationGateType.BROWSER_RENDER, BrowserViewportResult::renderStatus,
                        BrowserViewportResult::renderIssues),
                aggregate(ValidationGateType.ACCESSIBILITY, BrowserViewportResult::accessibilityStatus,
                        BrowserViewportResult::accessibilityIssues),
                aggregate(ValidationGateType.VISUAL_PARITY, BrowserViewportResult::visualStatus,
                        BrowserViewportResult::visualIssues));
    }

    private ValidationGateResult aggregate(
            ValidationGateType type,
            java.util.function.Function<BrowserViewportResult, BrowserGateStatus> status,
            java.util.function.Function<BrowserViewportResult, List<String>> issues) {
        List<String> collected = new ArrayList<>();
        long duration = 0;
        for (BrowserViewportResult viewport : viewports) {
            duration += viewport.durationMs();
            if (status.apply(viewport) != BrowserGateStatus.PASSED) {
                List<String> viewportIssues = issues.apply(viewport);
                if (viewportIssues.isEmpty()) {
                    collected.add(viewport.name() + ": " + status.apply(viewport));
                } else {
                    viewportIssues.forEach(issue -> collected.add(viewport.name() + ": " + issue));
                }
            }
        }
        if (viewports.isEmpty()) {
            collected.add("브라우저 검증 결과가 없습니다.");
        }
        return new ValidationGateResult(type, collected.isEmpty(), collected, duration, createdAt);
    }
}
