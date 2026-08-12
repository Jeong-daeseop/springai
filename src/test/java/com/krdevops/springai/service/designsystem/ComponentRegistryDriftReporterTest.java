package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** KRV-004: Registry 전체를 순회하며 logicalType별 Drift를 집계하는 보고서 검증. */
class ComponentRegistryDriftReporterTest {

    private final ComponentRegistryDriftReporter reporter =
            new ComponentRegistryDriftReporter(new FigmaPropertyDriftValidator());

    @Test
    void missingSnapshotIsReportedPerLogicalType() {
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.08", null,
                Map.of("krds.button", new ComponentRegistryEntry(
                        "BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                        ComponentRegistryEntry.LifecycleStatus.CURRENT, null,
                        List.of(), Map.of(), Map.of())));

        ComponentRegistryDriftReporter.DriftReport report = reporter.report(registry, Map.of());

        assertThat(report.hasDrift()).isTrue();
        assertThat(report.perType()).extracting(ComponentRegistryDriftReporter.LogicalTypeDrift::logicalType)
                .containsExactly("krds.button");
        assertThat(report.perType().get(0).issues()).extracting(DesignSystemIssue::code)
                .contains("COMPONENT_SNAPSHOT_MISSING");
    }

    @Test
    void nonCurrentLifecycleIsReportedEvenWithoutSnapshot() {
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.08", null,
                Map.of("krds.legacyButton", new ComponentRegistryEntry(
                        "LEGACY_SET", "LegacyButton", ComponentRegistryEntry.PublishStatus.UNPUBLISHED,
                        ComponentRegistryEntry.LifecycleStatus.DRAFT, null,
                        List.of(), Map.of(), Map.of())));

        ComponentRegistryDriftReporter.DriftReport report = reporter.report(registry, Map.of());

        assertThat(report.perType().get(0).issues()).extracting(DesignSystemIssue::code)
                .contains("COMPONENT_LIFECYCLE_NOT_CURRENT");
    }

    @Test
    void matchingContractAndSnapshotProduceNoDrift() {
        ComponentRegistryEntry entry = new ComponentRegistryEntry(
                "BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(),
                Map.of(), Map.of(), Set.of(), Set.of(), Map.of(), Set.of(), null, null, "2.0.0");
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0", "2026.08", null, Map.of("krds.button", entry));
        FigmaPropertyDriftValidator.LibraryComponentSnapshot snapshot =
                new FigmaPropertyDriftValidator.LibraryComponentSnapshot("BUTTON_SET", Map.of(), Map.of());

        ComponentRegistryDriftReporter.DriftReport report =
                reporter.report(registry, Map.of("krds.button", snapshot));

        assertThat(report.hasDrift()).isFalse();
        assertThat(report.totalIssueCount()).isZero();
    }

    @Test
    void nullRegistryProducesEmptyReport() {
        ComponentRegistryDriftReporter.DriftReport report = reporter.report(null, Map.of());

        assertThat(report.hasDrift()).isFalse();
    }
}
