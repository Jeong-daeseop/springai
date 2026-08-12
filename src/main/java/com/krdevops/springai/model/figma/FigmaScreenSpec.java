package com.krdevops.springai.model.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.krdevops.springai.model.design.role.ScreenPattern;
import java.util.List;

/**
 * R1: Figma 화면 스펙 — ScreenSpecification을 Figma 논리 트리로 투영.
 * 계획서 12번 R0-010, R5-020 기반.
 */
public record FigmaScreenSpec(
        @JsonProperty("screenId")
        String screenId,

        @JsonProperty("screenVersion")
        int screenVersion,

        @JsonProperty("screenSpecificationId")
        String screenSpecificationId,

        @JsonProperty("screenSpecificationVersion")
        int screenSpecificationVersion,

        @JsonProperty("screenType")
        FigmaScreenType screenType,

        @JsonProperty("layoutPattern")
        LayoutPattern layoutPattern,

        @JsonProperty("name")
        String name,

        @JsonProperty("route")
        String route,

        @JsonProperty("viewport")
        String viewport,

        @JsonProperty("status")
        String status,

        @JsonProperty("designSystem")
        DesignSystemRef designSystem,

        @JsonProperty("content")
        FigmaNodeSpec content,

        @JsonProperty("issues")
        List<FigmaExportIssue> issues,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @JsonProperty("semanticPattern")
        ScreenPattern semanticPattern,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @JsonProperty("screenPatternVersion")
        String screenPatternVersion,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @JsonProperty("variantRuleSetVersion")
        String variantRuleSetVersion,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @JsonProperty("componentContractVersion")
        String componentContractVersion
) {
    public static final String SCHEMA_VERSION = "1";
    public static final String SCHEMA_VERSION_V2 = "figma-screen-spec-v2";

    public record DesignSystemRef(
            @JsonProperty("profileId")
            String profileId,

            @JsonProperty("profileVersion")
            String profileVersion,

            @JsonProperty("registryVersion")
            String registryVersion
    ) {
    }

    public FigmaScreenSpec {
        if (screenId == null || screenId.isBlank()) {
            throw new IllegalArgumentException("screenId는 필수입니다");
        }
        if (screenVersion < 1) {
            throw new IllegalArgumentException("screenVersion은 1 이상이어야 합니다");
        }
        if (screenSpecificationId == null || screenSpecificationId.isBlank()) {
            throw new IllegalArgumentException("screenSpecificationId는 필수입니다");
        }
        if (screenSpecificationVersion < 1) {
            throw new IllegalArgumentException("screenSpecificationVersion은 1 이상이어야 합니다");
        }
        if (screenType == null) {
            throw new IllegalArgumentException("screenType은 필수입니다");
        }
        if (layoutPattern == null) {
            throw new IllegalArgumentException("layoutPattern은 필수입니다");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 필수입니다");
        }
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** Role·Variant v2 메타데이터 도입 전 호출자 호환. */
    public FigmaScreenSpec(
            String screenId, int screenVersion, String screenSpecificationId, int screenSpecificationVersion,
            FigmaScreenType screenType, LayoutPattern layoutPattern, String name, String route,
            String viewport, String status, DesignSystemRef designSystem, FigmaNodeSpec content,
            List<FigmaExportIssue> issues
    ) {
        this(screenId, screenVersion, screenSpecificationId, screenSpecificationVersion, screenType,
                layoutPattern, name, route, viewport, status, designSystem, content, issues,
                null, null, null, null);
    }
}
