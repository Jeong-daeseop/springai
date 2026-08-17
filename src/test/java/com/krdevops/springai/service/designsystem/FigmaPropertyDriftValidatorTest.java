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

    /**
     * R4-020: Registry에 등록된 componentSetKey가 실제 Figma Library Inventory Snapshot에
     * 존재하지 않으면(원격 import 결과 actual=null) FATAL로 차단해야 한다.
     */
    @Test
    void componentSetKeyAbsentFromRemoteInventoryIsFlaggedAsDrift() {
        ComponentRegistryEntry contract = contract(Set.of(), Map.of());

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, null);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("COMPONENT_PROPERTY_DRIFT");
            assertThat(issue.severity()).isEqualTo(DesignSystemIssue.Severity.FATAL);
        });
    }

    /** Registry Key가 실제 Figma Library의 다른 Component Set Key로 바뀌어 있으면 FATAL로 차단한다. */
    @Test
    void componentSetKeyMismatchWithRemoteInventoryIsFlaggedAsDrift() {
        ComponentRegistryEntry contract = contract(Set.of(), Map.of());
        FigmaPropertyDriftValidator.LibraryComponentSnapshot actual =
                new FigmaPropertyDriftValidator.LibraryComponentSnapshot("DIFFERENT_SET_KEY", Map.of(), Map.of());

        List<DesignSystemIssue> issues = validator.validate("krds.button", contract, actual);

        assertThat(issues).singleElement().satisfies(issue -> {
            assertThat(issue.code()).isEqualTo("COMPONENT_PROPERTY_DRIFT");
            assertThat(issue.severity()).isEqualTo(DesignSystemIssue.Severity.FATAL);
        });
    }
}
