package com.krdevops.springai.model.thymeleaf;

/**
 * `<form:input path="X">`, `<input name="X">`, `<select name="X">` 등 하나의 입력 필드 증거.
 * {@code fieldName}은 {@code path} 속성(Spring form 태그) 또는 {@code name} 속성(순수 HTML)에서 온다.
 */
public record JspFormFieldEvidence(
        String fieldName,
        String tagName,
        boolean hidden,
        boolean errorDisplay,
        String sourceLocation
) {
    public JspFormFieldEvidence {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName은 필수입니다.");
        }
        if (tagName == null || tagName.isBlank()) {
            throw new IllegalArgumentException("tagName은 필수입니다.");
        }
    }
}
