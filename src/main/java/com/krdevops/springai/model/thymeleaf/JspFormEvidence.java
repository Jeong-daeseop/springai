package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/**
 * `<form>` 또는 `<form:form>` 태그 1개의 증거. {@code modelAttribute}는 `form:form`의
 * {@code modelAttribute} 속성값이며, 순수 `<form>`은 이 값이 없다(null).
 */
public record JspFormEvidence(
        String formName,
        String modelAttribute,
        String rawAction,
        String resolvedRoute,
        String httpMethod,
        List<JspFormFieldEvidence> fields,
        String sourceLocation
) {
    public JspFormEvidence {
        httpMethod = (httpMethod == null || httpMethod.isBlank()) ? "GET" : httpMethod.toUpperCase(java.util.Locale.ROOT);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
