package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** 한 viewport에서 실행한 render/axe/visual Gate 결과와 파일 증적. */
public record BrowserViewportResult(
        String name,
        int width,
        int height,
        BrowserGateStatus renderStatus,
        BrowserGateStatus accessibilityStatus,
        BrowserGateStatus visualStatus,
        List<String> renderIssues,
        List<String> accessibilityIssues,
        List<AxeViolation> violations,
        String screenshotPath,
        String baselinePath,
        String diffPath,
        Double differenceRatio,
        List<String> visualIssues,
        long durationMs
) {
    public BrowserViewportResult {
        renderIssues = renderIssues == null ? List.of() : List.copyOf(renderIssues);
        accessibilityIssues = accessibilityIssues == null ? List.of() : List.copyOf(accessibilityIssues);
        violations = violations == null ? List.of() : List.copyOf(violations);
        visualIssues = visualIssues == null ? List.of() : List.copyOf(visualIssues);
    }
}
