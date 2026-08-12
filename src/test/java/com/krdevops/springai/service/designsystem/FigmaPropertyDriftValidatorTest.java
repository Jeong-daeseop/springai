package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** KRV-027: Registry Contract와 실제 Figma Library Snapshot의 Property·Variant Drift 검증. */
class FigmaPropertyDriftValidatorTest {

    private final FigmaPropertyDriftValidator validator = new FigmaPropertyDriftValidator();

    private ComponentRegistryEntry contract(Set<String> requiredProperties, Map<String, String> variants) {
        return new ComponentRegistryEntry(
                "BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(),
                variants,
                Map.of("style", new ComponentRegistryEntry.PropertyMapping(
                        "Style", ComponentRegistryEntry.PropertyType.VARIANT, Map.of())),
                Set.of(), Set.of(), Map.of(), requiredProperties, null, null, "2.0.0");
    }

    @Test
    void requiredPropertyMissingFromActualIsFlagged() {
        ComponentRegistryEntry contract = contract(Set.of("style"), Map.of());
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual =
                new FigmaPropertyDriftValidator.LibraryComponentSnapshot("BUTTON_SET", Map.of(), Map.of());

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("REQUIRED_COMPONENT_PROPERTY_MISSING");
    }

    @Test
    void requiredPropertyPresentInActualProducesNoMissingIssue() {
        ComponentRegistryEntry contract = contract(Set.of("style"), Map.of());
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot(
                "BUTTON_SET",
                Map.of("Style", new FigmaPropertyDriftValidator.ActualProperty("VARIANT", Set.of("Primary"))),
                Map.of());

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).extracting(DesignSystemIssue::code).doesNotContain("REQUIRED_COMPONENT_PROPERTY_MISSING");
    }

    @Test
    void contractVariantNameMissingFromActualSnapshotIsFlagged() {
        ComponentRegistryEntry contract = contract(Set.of(), Map.of("Style=Primary", "PRIMARY_KEY"));
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot(
                "BUTTON_SET", Map.of(), Map.of("Style=Secondary", "SECONDARY_KEY"));

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("COMPONENT_VARIANT_NAME_DRIFT");
    }

    @Test
    void actualSnapshotHasUndeclaredVariantIsFlagged() {
        ComponentRegistryEntry contract = contract(Set.of(), Map.of("Style=Primary", "PRIMARY_KEY"));
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot(
                "BUTTON_SET", Map.of(),
                Map.of("Style=Primary", "PRIMARY_KEY", "Style=Tertiary", "TERTIARY_KEY"));

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).extracting(DesignSystemIssue::code).contains("COMPONENT_VARIANT_NAME_DRIFT");
    }

    @Test
    void matchingVariantNamesProduceNoDriftIssue() {
        ComponentRegistryEntry contract = contract(Set.of(), Map.of("Style=Primary", "PRIMARY_KEY"));
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot(
                "BUTTON_SET", Map.of(), Map.of("Style=Primary", "PRIMARY_KEY"));

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).extracting(DesignSystemIssue::code).doesNotContain("COMPONENT_VARIANT_NAME_DRIFT");
    }

    @Test
    void emptyActualVariantsSkipsVariantNameDriftCheckForBackwardCompatibility() {
        ComponentRegistryEntry contract = contract(Set.of(), Map.of("Style=Primary", "PRIMARY_KEY"));
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual =
                new FigmaPropertyDriftValidator.LibraryComponentSnapshot("BUTTON_SET", Map.of(), Map.of());

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).extracting(DesignSystemIssue::code).doesNotContain("COMPONENT_VARIANT_NAME_DRIFT");
    }
}
