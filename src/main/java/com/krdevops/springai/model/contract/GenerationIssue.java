package com.krdevops.springai.model.contract;

/**
 * Figma Operation과 Thymeleaf 생성 Pipeline이 공유하는 문제 보고 값 객체.
 * {@code stage}는 도메인마다 다른 개방형 문자열이라 폐쇄 enum으로 두지 않는다
 * (예: Figma는 "CONTEXT_ANALYSIS", Thymeleaf는 "BINDING_CONTRACT").
 */
public record GenerationIssue(
        @jakarta.validation.constraints.NotBlank String code,
        @jakarta.validation.constraints.NotNull Severity severity,
        @jakarta.validation.constraints.NotBlank String stage,
        String sourceLocation,
        @jakarta.validation.constraints.NotBlank String message,
        String remediation
) {
    public GenerationIssue {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code는 필수입니다.");
        }
        if (severity == null) {
            throw new IllegalArgumentException("severity는 필수입니다.");
        }
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("stage는 필수입니다.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message는 필수입니다.");
        }
    }

    public enum Severity { FATAL, ERROR, WARNING }
}
