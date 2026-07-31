package com.krdevops.springai.model.figma;

/** Export 과정에서 발생한 문제. severity에 따라 전체 중단·기본값 대체·계속 진행이 갈린다(08번 §8). */
public record FigmaExportIssue(
        @jakarta.validation.constraints.NotBlank String code,
        @jakarta.validation.constraints.NotNull Severity severity,
        @jakarta.validation.constraints.NotBlank String message,
        String logicalNodeId,
        String jsonPointer
) {
    public FigmaExportIssue(String code, Severity severity, String message, String logicalNodeId) {
        this(code, severity, message, logicalNodeId, null);
    }

    public enum Severity { FATAL, ERROR, WARNING }
}
