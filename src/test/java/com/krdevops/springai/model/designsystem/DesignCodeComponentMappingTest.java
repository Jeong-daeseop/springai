package com.krdevops.springai.model.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignCodeComponentMappingTest {

    @Test
    void 승인Mapping계약을불변컬렉션으로정규화하고Json왕복한다() throws Exception {
        DesignCodeComponentMapping mapping = mapping(DesignCodeComponentMapping.Status.APPROVED,
                "reviewer", Instant.parse("2026-08-23T01:00:00Z"), List.of(property("Size", "size")));
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        DesignCodeComponentMapping restored = mapper.readValue(
                mapper.writeValueAsBytes(mapping), DesignCodeComponentMapping.class);

        assertThat(restored).isEqualTo(mapping);
        assertThat(restored.supportedRendererProfiles()).containsExactly("thymeleaf-krds");
        assertThatThrownBy(() -> restored.propertyMappings().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void 승인자와승인시각없는Approved를거부한다() {
        assertThatThrownBy(() -> mapping(DesignCodeComponentMapping.Status.APPROVED,
                null, null, List.of(property("Size", "size"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("approvedBy");
    }

    @Test
    void 중복Property와RendererProfile없는계약을거부한다() {
        assertThatThrownBy(() -> mapping(DesignCodeComponentMapping.Status.DRAFT,
                null, null, List.of(property("Size", "size"), property("Size", "density"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("figmaProperty");

        assertThatThrownBy(() -> new DesignCodeComponentMapping(
                "map-1", "1.0", DesignCodeComponentMapping.Status.DRAFT, "a".repeat(64),
                "button", "FIGMA_BUTTON", "fragments/button :: button", List.of(), List.of(),
                null, List.of(), "figma-r1", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("supportedRendererProfiles");
    }

    private static DesignCodeComponentMapping mapping(
            DesignCodeComponentMapping.Status status, String approvedBy, Instant approvedAt,
            List<DesignCodeComponentMapping.PropertyMapping> properties) {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", status, "a".repeat(64), "button", "FIGMA_BUTTON",
                "fragments/button :: button", properties,
                List.of(new DesignCodeComponentMapping.SlotMapping("Leading icon", "leadingIcon")),
                Map.of("label", "확인"), List.of("thymeleaf-krds", "thymeleaf-krds"),
                "figma-r1", approvedBy, approvedAt);
    }

    private static DesignCodeComponentMapping.PropertyMapping property(String figma, String parameter) {
        return new DesignCodeComponentMapping.PropertyMapping(
                figma, parameter, Map.of("Small", "sm"), true, "md", null);
    }
}
