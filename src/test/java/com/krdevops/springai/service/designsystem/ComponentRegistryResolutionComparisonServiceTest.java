package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRegistryResolutionComparisonServiceTest {

    @Test
    void observationComparisonReportsPublishedKeyDrift() {
        ComponentRegistry legacy = new ComponentRegistry("krds", "2.0", "2.0",
                new ComponentRegistry.LibraryRef("L", "KRDS"),
                Map.of("button", new ComponentRegistryEntry("OLD_SET", "Button",
                        ComponentRegistryEntry.PublishStatus.CURRENT, Map.of(), Map.of())), Map.of());
        ComponentCatalog.Entry contract = new ComponentCatalog.Entry(ComponentCatalog.Kind.COMPONENT,
                ComponentCatalog.Requirement.REQUIRED, List.of(), null, Map.of(), List.of(), Set.of(), Set.of(),
                Set.of(), null, null);
        ComponentRegistrySnapshotV3.Binding binding = new ComponentRegistrySnapshotV3.Binding("NEW_SET", "Button",
                ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.CURRENT, Map.of());
        ResolvedComponentRegistry resolved = new ResolvedComponentRegistry("2.0", "a".repeat(64), "krds", "2.0",
                "3.0", "b".repeat(64), Map.of("button", new ResolvedComponentRegistry.ResolvedEntry(
                        "button", "button", contract, List.of(new ResolvedComponentRegistry.AtomicBinding("button", contract, binding)), List.of("button"))));

        var result = new ComponentRegistryResolutionComparisonService().compare(legacy, resolved);

        assertThat(result.identical()).isFalse();
        assertThat(result.differences()).extracting(ComponentRegistryResolutionComparisonService.Difference::code)
                .contains("COMPONENT_KEY_CHANGED");
    }
}
