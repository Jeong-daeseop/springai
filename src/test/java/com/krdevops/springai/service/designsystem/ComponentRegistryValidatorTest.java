package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** R1-T03: Registry 중복 Component Key 검증. */
class ComponentRegistryValidatorTest {

    private final ComponentRegistryValidator validator = new ComponentRegistryValidator();

    @Test
    void detectsTwoLogicalTypesSharingTheSameComponentSetKey() {
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.07", null,
                Map.of(
                        "krds.button", new ComponentRegistryEntry("SHARED_KEY", Map.of()),
                        "krds.iconButton", new ComponentRegistryEntry("SHARED_KEY", Map.of())));

        List<DesignSystemIssue> issues = validator.validate(registry);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("DUPLICATE_COMPONENT_KEY");
    }

    @Test
    void distinctComponentSetKeysProduceNoDuplicateIssue() {
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.07", null,
                Map.of(
                        "krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of()),
                        "krds.textField", new ComponentRegistryEntry("TEXT_FIELD_KEY", Map.of())));

        List<DesignSystemIssue> issues = validator.validate(registry);

        assertThat(issues).extracting(DesignSystemIssue::code).doesNotContain("DUPLICATE_COMPONENT_KEY");
    }

    @Test
    void rejectsConflictingAliasAndReplacementCycle() {
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.07", null,
                Map.of(
                        "krds.a", new ComponentRegistryEntry(
                                "A_KEY", "A", ComponentRegistryEntry.PublishStatus.CURRENT,
                                ComponentRegistryEntry.LifecycleStatus.DEPRECATED, "krds.b",
                                List.of("krds.b"), Map.of(), Map.of()),
                        "krds.b", new ComponentRegistryEntry(
                                "B_KEY", "B", ComponentRegistryEntry.PublishStatus.CURRENT,
                                ComponentRegistryEntry.LifecycleStatus.DEPRECATED, "krds.a",
                                List.of(), Map.of(), Map.of())));

        List<DesignSystemIssue> issues = validator.validate(registry);

        assertThat(issues).extracting(DesignSystemIssue::code)
                .contains("COMPONENT_ALIAS_CONFLICT", "COMPONENT_REPLACEMENT_CYCLE");
    }

    @Test
    void variantPropertyWithoutValuesIsWarned() {
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.07", null,
                Map.of("krds.button", new ComponentRegistryEntry("BUTTON_KEY",
                        Map.of("variant", new ComponentRegistryEntry.PropertyMapping(
                                "Type", ComponentRegistryEntry.PropertyType.VARIANT, Map.of())))));

        List<DesignSystemIssue> issues = validator.validate(registry);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("VARIANT_PROPERTY_WITHOUT_VALUES");
    }
}
