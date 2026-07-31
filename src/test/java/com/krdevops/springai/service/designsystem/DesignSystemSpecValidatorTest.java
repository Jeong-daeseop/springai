package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignSystemSpecValidatorTest {

    private final DesignSystemSpecValidator validator = new DesignSystemSpecValidator();

    @Test
    void detectsDuplicateComponentId() {
        DesignSystemSpec.ComponentDefinition button = componentWithoutVariant("krds.button");
        DesignSystemSpec spec = spec(List.of(button, button), List.of());

        List<DesignSystemIssue> issues = validator.validate(spec);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("DUPLICATE_COMPONENT_ID");
    }

    @Test
    void detectsPatternReferencingUnknownComponent() {
        DesignSystemSpec.PatternDefinition pattern = new DesignSystemSpec.PatternDefinition(
                "egov.actionArea", "ActionArea", List.of("krds.doesNotExist"));
        DesignSystemSpec spec = spec(List.of(), List.of(pattern));

        List<DesignSystemIssue> issues = validator.validate(spec);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("PATTERN_UNKNOWN_COMPONENT");
    }

    @Test
    void detectsVariantPropertyWithoutDeclaredOptions() {
        DesignSystemSpec.ComponentDefinition.Property variantProperty = new DesignSystemSpec.ComponentDefinition.Property(
                "Type", DesignSystemSpec.ComponentDefinition.PropertyType.VARIANT, "Primary");
        DesignSystemSpec.ComponentDefinition component = new DesignSystemSpec.ComponentDefinition(
                "krds.button", "KRDS/Button", null, List.of(variantProperty), Map.of());
        DesignSystemSpec spec = spec(List.of(component), List.of());

        List<DesignSystemIssue> issues = validator.validate(spec);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("VARIANT_PROPERTY_WITHOUT_OPTIONS");
    }

    @Test
    void validSpecProducesNoIssues() {
        DesignSystemSpec.ComponentDefinition component = componentWithoutVariant("krds.button");
        DesignSystemSpec.PatternDefinition pattern = new DesignSystemSpec.PatternDefinition(
                "egov.actionArea", "ActionArea", List.of("krds.button"));
        DesignSystemSpec spec = spec(List.of(component), List.of(pattern));

        assertThat(validator.validate(spec)).isEmpty();
    }

    @Test
    void validatesDeveloperMetadataAndLayoutRange() {
        var invalid = new DesignSystemSpec.ComponentDefinition(
                "krds.button", "KRDS/Button", "버튼",
                new DesignSystemSpec.ComponentDefinition.DeveloperMetadata(
                        "KrdsButton", "not-a-url", "com.krdevops.ui"),
                new DesignSystemSpec.ComponentDefinition.Layout(
                        "HORIZONTAL", "16", "12", "8", "CENTER",
                        "500", "100", "40", "56"),
                List.of(), Map.of());

        assertThat(validator.validate(spec(List.of(invalid), List.of())))
                .extracting(DesignSystemIssue::code)
                .contains("INVALID_DOCUMENTATION_URL", "INVALID_LAYOUT_RANGE");
    }

    private DesignSystemSpec.ComponentDefinition componentWithoutVariant(String id) {
        return new DesignSystemSpec.ComponentDefinition(id, "KRDS/Button", null, List.of(), Map.of());
    }

    private DesignSystemSpec spec(
            List<DesignSystemSpec.ComponentDefinition> components,
            List<DesignSystemSpec.PatternDefinition> patterns) {
        return new DesignSystemSpec("krds", "KRDS Design System", "1.0",
                List.of(), List.of(), components, patterns, List.of());
    }
}
