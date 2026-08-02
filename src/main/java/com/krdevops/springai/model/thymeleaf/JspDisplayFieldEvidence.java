package com.krdevops.springai.model.thymeleaf;

/**
 * `<c:out value="${result.emplyrId}"/>`처럼 특정 root의 필드를 화면에 표시하는 증거.
 * {@code rootAttributeName}은 forEach loop 변수 또는 Model attribute 이름이다.
 */
public record JspDisplayFieldEvidence(
        String rootAttributeName,
        String fieldName,
        String sourceLocation
) {
    public JspDisplayFieldEvidence {
        if (rootAttributeName == null || rootAttributeName.isBlank()) {
            throw new IllegalArgumentException("rootAttributeName은 필수입니다.");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName은 필수입니다.");
        }
    }
}
