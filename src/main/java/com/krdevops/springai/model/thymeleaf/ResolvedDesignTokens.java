package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * R6-056: Design Token 로드·매핑 산출물.
 *
 * <p>회사 표준 DesignSystemProfile에서 로드한 semantic token들을
 * CSS Variable 및 Component Property로 해석한 결과를 표현한다.
 * AppliedDesignRules (R6-055)와 병합되어 우선순위가 적용된다.
 */
public record ResolvedDesignTokens(
        String profileId,
        String profileVersion,
        @Nullable String designMdHash,
        Map<String, String> colorTokens,
        Map<String, String> typographyTokens,
        Map<String, String> spacingTokens,
        Map<String, String> radiusTokens,
        Map<String, String> layoutTokens,
        Map<String, ComponentPropertyTokens> componentTokens,
        List<GenerationIssue> issues
) {
    /**
     * Component별 Property 매핑.
     * 예: button → {variant: "krds-btn-primary", size: "medium"}
     */
    public record ComponentPropertyTokens(
            String componentName,
            Map<String, String> propertyBindings
    ) {}

    public ResolvedDesignTokens {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId는 필수입니다.");
        }
        if (profileVersion == null || profileVersion.isBlank()) {
            throw new IllegalArgumentException("profileVersion은 필수입니다.");
        }
        if (colorTokens == null || typographyTokens == null || spacingTokens == null
                || radiusTokens == null || layoutTokens == null || componentTokens == null) {
            throw new IllegalArgumentException("모든 token 맵은 null이 아니어야 합니다.");
        }
        if (issues == null) {
            throw new IllegalArgumentException("issues는 null이 아니어야 합니다.");
        }

        colorTokens = Map.copyOf(colorTokens);
        typographyTokens = Map.copyOf(typographyTokens);
        spacingTokens = Map.copyOf(spacingTokens);
        radiusTokens = Map.copyOf(radiusTokens);
        layoutTokens = Map.copyOf(layoutTokens);
        componentTokens = Map.copyOf(componentTokens);
        issues = List.copyOf(issues);
    }

    /**
     * 이 ResolvedDesignTokens에 FATAL 이슈가 있는지 확인.
     */
    public boolean hasFatalIssue() {
        return issues.stream().anyMatch(i -> i.severity() == GenerationIssue.Severity.FATAL);
    }
}
