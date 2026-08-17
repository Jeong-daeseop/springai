package com.krdevops.springai.model.thymeleaf;

/**
 * R6-050: Design-aware Thymeleaf Generator의 고정 10단계 계약.
 *
 * <p>선언 순서가 실행 순서이며 영속 보고서와 재시도 판단에서도 이 순서를 단일 기준으로 사용한다.
 * 단계 추가·삭제·재정렬은 {@link ThymeleafGenerationPipelineContract#CONTRACT_VERSION}을 올리는
 * 호환성 변경이다.
 */
public enum ThymeleafGenerationStage {
    SOURCE_ANALYSIS(1, "LegacyConversionRequest", "LegacyScreenAnalysis", RetryPolicy.AFTER_INPUT_CHANGE),
    BINDING_CONTRACT(2, "LegacyScreenAnalysis", "ThymeleafBindingContract", RetryPolicy.AFTER_INPUT_CHANGE),
    SCREEN_TYPE_DECISION(3, "ThymeleafBindingContract", "ScreenTypeDecision", RetryPolicy.SAME_INPUT_IDEMPOTENT),
    COMPONENT_INVENTORY(4, "ScreenTypeDecision+ComponentRegistrySnapshot", "SelectedComponentInventory",
            RetryPolicy.AFTER_INPUT_CHANGE),
    DESIGN_MD_RULES(5, "SelectedComponentInventory+DESIGN.md", "AppliedDesignRules",
            RetryPolicy.AFTER_INPUT_CHANGE),
    COMPANY_TOKEN_MAPPING(6, "AppliedDesignRules+DesignSystemProfileSnapshot", "ResolvedDesignTokens",
            RetryPolicy.AFTER_INPUT_CHANGE),
    HTML_SKELETON(7, "Binding+Component+Rules+Tokens", "ThymeleafSkeleton",
            RetryPolicy.SAME_INPUT_IDEMPOTENT),
    MODEL_BINDING(8, "ThymeleafSkeleton+ThymeleafBindingContract", "BoundThymeleafView",
            RetryPolicy.SAME_INPUT_IDEMPOTENT),
    RESPONSIVE_TRANSFORMATION(9, "BoundThymeleafView+PlatformPolicy", "ResponsiveThymeleafViewSet",
            RetryPolicy.SAME_INPUT_IDEMPOTENT),
    BUILD_RENDER_PARITY_VALIDATION(10, "GeneratedProject+ResponsiveThymeleafViewSet+Fixtures",
            "ThymeleafGenerationReport", RetryPolicy.AFTER_ENVIRONMENT_RECOVERY);

    private final int order;
    private final String inputContract;
    private final String outputContract;
    private final RetryPolicy retryPolicy;

    ThymeleafGenerationStage(int order, String inputContract, String outputContract, RetryPolicy retryPolicy) {
        this.order = order;
        this.inputContract = inputContract;
        this.outputContract = outputContract;
        this.retryPolicy = retryPolicy;
    }

    public int order() {
        return order;
    }

    public String inputContract() {
        return inputContract;
    }

    public String outputContract() {
        return outputContract;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    /** 자동 재시도는 하지 않는다. 재시도는 항상 동일 generation의 명시적 요청이다. */
    public enum RetryPolicy {
        SAME_INPUT_IDEMPOTENT,
        AFTER_INPUT_CHANGE,
        AFTER_ENVIRONMENT_RECOVERY
    }
}
