package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentVariantValueResolverTest {

    private final ComponentPropertyParameterResolver propertyResolver =
            new ComponentPropertyParameterResolver();
    private final ComponentVariantValueResolver variantResolver =
            new ComponentVariantValueResolver();

    @Test
    void figmaEnum과Boolean을Fragment계약값으로변환한다() {
        DesignCodeComponentMapping mapping = mapping(null);
        ComponentPropertyParameterResolver.Resolution properties = propertyResolver.requireResolved(
                mapping, Map.of("Style", "Primary", "Disabled", true, "Label", "저장"));

        ComponentVariantValueResolver.Resolution result =
                variantResolver.requireResolved(mapping, properties);

        assertThat(result.fragmentParameters()).containsExactly(
                Map.entry("variant", "primary"),
                Map.entry("disabled", true),
                Map.entry("label", "저장"));
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void valueMapping없는일반값과이미Fragment계약인기본값은그대로둔다() {
        DesignCodeComponentMapping mapping = mapping(null);
        ComponentPropertyParameterResolver.Resolution properties = propertyResolver.requireResolved(
                mapping, Map.of("Style", "Ghost", "Label", "취소"));

        ComponentVariantValueResolver.Resolution result =
                variantResolver.requireResolved(mapping, properties);

        assertThat(result.fragmentParameters()).containsEntry("variant", "ghost")
                .containsEntry("disabled", false)
                .containsEntry("label", "취소");
    }

    @Test
    void 미지원Variant는명시적Fallback이있을때만허용한다() {
        DesignCodeComponentMapping mapping = mapping("secondary");
        ComponentPropertyParameterResolver.Resolution properties = propertyResolver.requireResolved(
                mapping, Map.of("Style", "Unknown", "Label", "계속"));

        ComponentVariantValueResolver.Resolution result =
                variantResolver.requireResolved(mapping, properties);

        assertThat(result.fragmentParameters()).containsEntry("variant", "secondary");
        assertThat(result.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("VARIANT_FALLBACK_APPLIED");
            assertThat(issue.rejectedValue()).isEqualTo("Unknown");
        });
    }

    @Test
    void 미지원Variant에Fallback이없으면값을제거하고Apply를차단한다() {
        DesignCodeComponentMapping mapping = mapping(null);
        ComponentPropertyParameterResolver.Resolution properties = propertyResolver.requireResolved(
                mapping, Map.of("Style", "Unknown", "Label", "계속"));

        ComponentVariantValueResolver.Resolution preview = variantResolver.resolve(mapping, properties);

        assertThat(preview.valid()).isFalse();
        assertThat(preview.fragmentParameters()).doesNotContainKey("variant");
        assertThat(preview.issues()).extracting(ComponentVariantValueResolver.ConversionIssue::code)
                .containsExactly("VARIANT_VALUE_UNSUPPORTED");
        assertThatThrownBy(() -> variantResolver.requireResolved(mapping, properties))
                .isInstanceOf(ComponentVariantValueResolver.ComponentVariantResolutionException.class);
    }

    @Test
    void 다른Version의PropertyResolution을재사용할수없다() {
        DesignCodeComponentMapping mapping = mapping(null);
        ComponentPropertyParameterResolver.Resolution wrong =
                new ComponentPropertyParameterResolver.Resolution(
                        mapping.mappingId(), "2.0", mapping.thymeleafFragment(), Map.of(),
                        java.util.Set.of(), java.util.Set.of(), List.of());

        assertThatThrownBy(() -> variantResolver.resolve(mapping, wrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ID·Version");
    }

    private DesignCodeComponentMapping mapping(Object styleFallback) {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.APPROVED, "a".repeat(64),
                "button", "FIGMA_BUTTON", "fragments/button :: button",
                List.of(
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Style", "variant",
                                Map.of("Primary", "primary", "Ghost", "ghost"),
                                true, null, styleFallback),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Disabled", "disabled", Map.of("true", true, "false", false),
                                false, false, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Label", "label", Map.of(), true, null, null)),
                List.of(), null, List.of("thymeleaf-krds"), "figma-r1", "reviewer",
                Instant.parse("2026-08-23T01:00:00Z"));
    }
}
