package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/**
 * VO 필드 1개와 JSP 표현(있다면)을 연결한 최종 바인딩. {@code provenance}는 개방형 문자열이며
 * "Controller·VO가 JSP hint보다 우선"이라는 §I-2E 규칙에 따라 항상 VO 필드를 기준으로 만들어진다.
 */
public record ThymeleafFieldBinding(
        String fieldName,
        String javaType,
        boolean writable,
        boolean required,
        List<String> validationAnnotations,
        String boundJspTag,
        String provenance,
        double confidence
) {
    public ThymeleafFieldBinding {
        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName은 필수입니다.");
        }
        if (javaType == null || javaType.isBlank()) {
            throw new IllegalArgumentException("javaType은 필수입니다.");
        }
        if (provenance == null || provenance.isBlank()) {
            throw new IllegalArgumentException("provenance는 필수입니다.");
        }
        validationAnnotations = validationAnnotations == null ? List.of() : List.copyOf(validationAnnotations);
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence는 0.0~1.0 사이여야 합니다.");
        }
    }
}
