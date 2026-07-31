package com.krdevops.springai.model.figma;

import java.time.LocalDateTime;
import java.util.List;

/** FigmaScreenExportService.export() 실행 결과. figma-generation-report-v1 계약으로 직렬화된다. */
public record FigmaExportResult(
        Status status,
        FigmaScreenSpec figmaScreenSpec,
        List<FigmaExportIssue> issues,
        LocalDateTime generatedAt,
        ArtifactRef artifact
) {
    public FigmaExportResult(Status status, FigmaScreenSpec figmaScreenSpec,
                             List<FigmaExportIssue> issues, LocalDateTime generatedAt) {
        this(status, figmaScreenSpec, issues, generatedAt, null);
    }

    public FigmaExportResult {
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다.");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
    }

    public record ArtifactRef(String artifactId, String relativePath) {
    }

    public enum Status { SUCCESS, PARTIAL, FAILED }
}
