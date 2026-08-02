package com.krdevops.springai.model.thymeleaf;

import java.util.List;

/** JSP가 실제로 제출/조회하는 route와, 그 route를 처리하는 Controller 메서드의 증거를 묶은 값. */
public record ThymeleafRouteBinding(
        String route,
        String httpMethod,
        String controllerMethodName,
        String modelAttributeName,
        String modelAttributeType,
        boolean validated,
        boolean redirect,
        List<String> securityEvidence
) {
    public ThymeleafRouteBinding {
        if (route == null || route.isBlank()) {
            throw new IllegalArgumentException("route는 필수입니다.");
        }
        if (httpMethod == null || httpMethod.isBlank()) {
            throw new IllegalArgumentException("httpMethod는 필수입니다.");
        }
        httpMethod = httpMethod.toUpperCase(java.util.Locale.ROOT);
        securityEvidence = securityEvidence == null ? List.of() : List.copyOf(securityEvidence);
    }
}
