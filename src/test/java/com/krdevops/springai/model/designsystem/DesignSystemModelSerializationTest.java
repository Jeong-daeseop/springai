package com.krdevops.springai.model.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R1-T01: DesignSystemSpec DTO 직렬화 round-trip 검증. */
class DesignSystemModelSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void designSystemSpecRoundTripsThroughJson() throws Exception {
        DesignSystemSpec.ComponentDefinition button = new DesignSystemSpec.ComponentDefinition(
                "krds.button", "KRDS/Button",
                new DesignSystemSpec.ComponentDefinition.Layout("HORIZONTAL", "{spacing.16}", "{spacing.12}", "{spacing.8}", "CENTER"),
                List.of(new DesignSystemSpec.ComponentDefinition.Property(
                        "Type", DesignSystemSpec.ComponentDefinition.PropertyType.VARIANT, "Primary")),
                Map.of("Type", List.of("Primary", "Secondary", "Tertiary")));
        DesignSystemSpec spec = new DesignSystemSpec(
                "krds", "KRDS Design System", "1.0",
                List.of(new DesignSystemSpec.Token("COLOR", "color.primary", "#0B5FFF")),
                List.of(new DesignSystemSpec.VariableCollection(
                        "Colors", List.of("Light", "Dark"),
                        Map.of("color.primary", Map.of("Light", "#0B5FFF", "Dark", "#4C8DFF")))),
                List.of(button),
                List.of(new DesignSystemSpec.PatternDefinition("egov.actionArea", "ActionArea", List.of("krds.button"))),
                List.of());

        String json = objectMapper.writeValueAsString(spec);
        DesignSystemSpec restored = objectMapper.readValue(json, DesignSystemSpec.class);

        assertThat(restored).isEqualTo(spec);
    }
}
