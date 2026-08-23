package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentPropertyParameterResolverTest {

    private final ComponentPropertyParameterResolver resolver = new ComponentPropertyParameterResolver();

    @Test
    void figmaProperty를Mapping순서대로FragmentParameter에투영한다() {
        ComponentPropertyParameterResolver.Resolution result = resolver.requireResolved(
                mapping(DesignCodeComponentMapping.Status.APPROVED),
                Map.of("Label", "저장", "Disabled", true));

        assertThat(result.valid()).isTrue();
        assertThat(result.fragmentParameters()).containsExactly(
                Map.entry("label", "저장"), Map.entry("disabled", true), Map.entry("size", "medium"));
        assertThat(result.consumedFigmaProperties()).containsExactly("Label", "Disabled");
        assertThat(result.issues()).extracting(ComponentPropertyParameterResolver.ResolutionIssue::code)
                .containsExactly("DEFAULT_VALUE_APPLIED");
    }

    @Test
    void 필수Property가없고기본값도없으면Apply경계를차단한다() {
        ComponentPropertyParameterResolver.Resolution preview = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.APPROVED), Map.of("Disabled", false));

        assertThat(preview.valid()).isFalse();
        assertThat(preview.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("REQUIRED_PROPERTY_MISSING");
            assertThat(issue.target()).isEqualTo("Label");
        });
        assertThatThrownBy(() -> resolver.requireResolved(
                mapping(DesignCodeComponentMapping.Status.APPROVED), Map.of("Disabled", false)))
                .isInstanceOf(ComponentPropertyParameterResolver.ComponentPropertyResolutionException.class);
    }

    @Test
    void 승인되지않은Mapping은값이완전해도차단한다() {
        ComponentPropertyParameterResolver.Resolution result = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.REVIEW_REQUIRED),
                Map.of("Label", "저장", "Disabled", false));

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(ComponentPropertyParameterResolver.ResolutionIssue::code)
                .contains("MAPPING_NOT_APPROVED");
    }

    @Test
    void 매핑되지않은Property는버리지않고경고근거로보존한다() {
        ComponentPropertyParameterResolver.Resolution result = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.APPROVED),
                Map.of("Label", "저장", "Disabled", false, "Unknown", "value"));

        assertThat(result.valid()).isTrue();
        assertThat(result.unmappedFigmaProperties()).containsExactly("Unknown");
        assertThat(result.issues()).anySatisfy(issue -> {
            assertThat(issue.code()).isEqualTo("UNMAPPED_FIGMA_PROPERTY");
            assertThat(issue.target()).isEqualTo("Unknown");
        });
    }

    @Test
    void 입력과출력컬렉션은호출뒤변경의영향을받지않는다() {
        LinkedHashMap<String, Object> input = new LinkedHashMap<>();
        input.put("Label", "저장");
        input.put("Disabled", false);
        ComponentPropertyParameterResolver.Resolution result = resolver.resolve(
                mapping(DesignCodeComponentMapping.Status.APPROVED), input);

        input.put("Label", "삭제");

        assertThat(result.fragmentParameters().get("label")).isEqualTo("저장");
        assertThatThrownBy(() -> result.fragmentParameters().put("label", "수정"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private DesignCodeComponentMapping mapping(DesignCodeComponentMapping.Status status) {
        String approvedBy = status == DesignCodeComponentMapping.Status.APPROVED ? "reviewer" : null;
        Instant approvedAt = status == DesignCodeComponentMapping.Status.APPROVED
                ? Instant.parse("2026-08-23T01:00:00Z") : null;
        return new DesignCodeComponentMapping(
                "map-button", "1.0", status, "a".repeat(64), "button", "FIGMA_BUTTON",
                "fragments/button :: button",
                List.of(
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Label", "label", Map.of(), true, null, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Disabled", "disabled", Map.of(), false, false, null),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Size", "size", Map.of(), false, "medium", null)),
                List.of(), null, List.of("thymeleaf-krds"), "figma-r1", approvedBy, approvedAt);
    }
}
