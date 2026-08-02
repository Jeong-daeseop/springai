package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** Controller 메서드 1개의 매핑·모델·반환뷰·보안 증거. */
public record ControllerMethodEvidence(
        String methodName,
        String httpMethod,
        String route,
        String modelAttributeParamName,
        String modelAttributeType,
        boolean validated,
        List<String> modelAttributesAdded,
        String returnViewOrRedirect,
        boolean redirect,
        List<String> securityEvidence,
        String sourceLocation
) {
    public ControllerMethodEvidence {
        if (methodName == null || methodName.isBlank()) {
            throw new IllegalArgumentException("methodName은 필수입니다.");
        }
        if (httpMethod == null || httpMethod.isBlank()) {
            throw new IllegalArgumentException("httpMethod는 필수입니다.");
        }
        if (route == null || route.isBlank()) {
            throw new IllegalArgumentException("route는 필수입니다.");
        }
        httpMethod = httpMethod.toUpperCase(java.util.Locale.ROOT);
        modelAttributesAdded = modelAttributesAdded == null ? List.of() : List.copyOf(modelAttributesAdded);
        securityEvidence = securityEvidence == null ? List.of() : List.copyOf(securityEvidence);
    }

    /** {@code returnViewOrRedirect}에서 뷰 이름의 마지막 경로 세그먼트(JSP 파일명과 비교하는 데 사용). */
    public String viewBaseName() {
        if (returnViewOrRedirect == null || redirect) {
            return null;
        }
        String withoutQuery = returnViewOrRedirect.split("[?]", 2)[0];
        int lastSlash = withoutQuery.lastIndexOf('/');
        return lastSlash < 0 ? withoutQuery : withoutQuery.substring(lastSlash + 1);
    }
}
