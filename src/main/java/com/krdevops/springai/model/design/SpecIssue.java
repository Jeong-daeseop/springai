package com.krdevops.springai.model.design;

public record SpecIssue(
        String code,
        Severity severity,
        String message,
        String fieldId
) {
    public enum Severity { INFO, WARNING, ERROR }
}
