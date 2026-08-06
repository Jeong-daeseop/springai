package com.krdevops.springai.model.thymeleaf;

/**
 * I-5C: 검증 Gate 유형.
 */
public enum ValidationGateType {
    THYMELEAF_PARSE("Thymeleaf 파싱 검증"),
    TEMPLATE_ENGINE_RENDER("실제 Spring TemplateEngine 렌더 검증"),
    BINDING_VALIDATION("바인딩 계약 검증"),
    ROUTE_PARITY("라우트 일치성 검증"),
    OVERFLOW_CHECK("오버플로우 검증"),
    BUILD_VALIDATION("빌드 검증"),
    BROWSER_RENDER("Playwright 브라우저 렌더 검증"),
    ACCESSIBILITY("axe 접근성 검증"),
    VISUAL_PARITY("스크린샷 시각 일치성 검증");

    public final String description;

    ValidationGateType(String description) {
        this.description = description;
    }
}
