package com.krdevops.springai.model.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;

/**
 * R1: Figma 화면 생성/갱신 결과.
 * 계획서 12번 R0-014, R5-036 기반.
 */
public record FigmaExportResult(
        @JsonProperty("status")
        Status status,

        @JsonProperty("figmaScreenSpec")
        FigmaScreenSpec figmaScreenSpec,

        @JsonProperty("issues")
        List<FigmaExportIssue> issues,

        @JsonProperty("generatedAt")
        LocalDateTime generatedAt,

        @JsonProperty("artifactRef")
        ArtifactRef artifactRef
) {
    public enum Status {
        SUCCESS,
        PARTIAL,
        PARTIAL_SUCCESS,
        FAILED
    }

    public record ArtifactRef(
            @JsonProperty("artifactId")
            String artifactId,

            @JsonProperty("relativePath")
            String relativePath
    ) {
    }

    public FigmaExportResult {
        if (status == null) {
            throw new IllegalArgumentException("status는 필수입니다");
        }
    }
}
