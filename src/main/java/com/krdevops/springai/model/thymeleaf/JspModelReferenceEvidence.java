package com.krdevops.springai.model.thymeleaf;

/**
 * JSP EL 표현식이 참조하는 최상위 식별자 1개의 증거(예: {@code ${searchVO.pageIndex}}의 {@code searchVO}).
 * {@code usageKind}는 개방형 문자열이다("EL_EXPRESSION", "FOR_EACH_ITEMS" 등).
 */
public record JspModelReferenceEvidence(
        String attributeName,
        String usageKind,
        String sourceLocation
) {
    public JspModelReferenceEvidence {
        if (attributeName == null || attributeName.isBlank()) {
            throw new IllegalArgumentException("attributeName은 필수입니다.");
        }
        if (usageKind == null || usageKind.isBlank()) {
            throw new IllegalArgumentException("usageKind는 필수입니다.");
        }
    }
}
