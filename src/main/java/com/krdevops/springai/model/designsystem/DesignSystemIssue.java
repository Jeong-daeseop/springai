package com.krdevops.springai.model.designsystem;

/** DesignSystemSpec/Profile/Registry 검증에서 공통으로 쓰는 문제 표현. */
public record DesignSystemIssue(
        String code,
        Severity severity,
        String message,
        String targetId
) {
    public enum Severity { FATAL, ERROR, WARNING }
}
