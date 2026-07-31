package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.design.ScreenSpecStatus;

import java.util.List;

/**
 * 승인된 ScreenSpecification을 Figma 업무 화면 생성에 적합한 논리 컴포넌트 트리로
 * Projection한 출력 계약. ScreenSpecification을 그대로 노출하지 않고 별도 DTO로 둔다.
 */
public record FigmaScreenSpec(
        @jakarta.validation.constraints.NotBlank String screenId,
        @jakarta.validation.constraints.Positive int screenVersion,
        @jakarta.validation.constraints.NotBlank String screenSpecificationId,
        @jakarta.validation.constraints.Positive int screenSpecificationVersion,
        @jakarta.validation.constraints.NotNull FigmaScreenType screenType,
        @jakarta.validation.constraints.NotNull LayoutPattern layoutPattern,
        @jakarta.validation.constraints.NotBlank String name,
        String route,
        String viewport,
        @jakarta.validation.constraints.NotNull ScreenSpecStatus status,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull DesignSystemRef designSystem,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull FigmaNodeSpec content,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull List<FigmaExportIssue> issues
) {
    public static final String SCHEMA_VERSION = "figma-screen-spec-v1";

    public FigmaScreenSpec {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다.");
        }
        if (screenType == null) {
            throw new IllegalArgumentException("screenType은 필수입니다.");
        }
        layoutPattern = layoutPattern == null ? LayoutPattern.STANDARD : layoutPattern;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public record DesignSystemRef(
            @jakarta.validation.constraints.NotBlank String profileId,
            @jakarta.validation.constraints.NotBlank String profileVersion,
            @jakarta.validation.constraints.NotBlank String registryVersion) {
    }
}
