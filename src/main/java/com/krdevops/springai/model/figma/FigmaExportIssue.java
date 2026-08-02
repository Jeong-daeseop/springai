package com.krdevops.springai.model.figma;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * R1: Figma 화면 생성 중 발생한 이슈.
 * 계획서 12번 문서 R5-034 기반.
 */
public record FigmaExportIssue(
        @JsonProperty("code")
        String code,

        @JsonProperty("severity")
        Severity severity,

        @JsonProperty("message")
        String message,

        @JsonProperty("logicalNodeId")
        String logicalNodeId,

        @JsonProperty("jsonPointer")
        String jsonPointer,

        @JsonProperty("metadata")
        Object metadata
) {
    public enum Severity {
        FATAL,
        ERROR,
        WARNING,
        INFO
    }

    public FigmaExportIssue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code는 필수입니다");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity는 필수입니다");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message는 필수입니다");
        }
    }
}
