package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRegistryInventoryValidatorTest {
    @Test
    void detectsPublishedVariantDriftAgainstInventory() {
        var binding = new ComponentRegistrySnapshotV3.Binding("SET", "Button",
                ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.CURRENT,
                Map.of("primary", "PRIMARY"));
        var registry = new ComponentRegistrySnapshotV3("component-registry-v3", "krds", "2", "3", "2",
                "a".repeat(64), null, Map.of("button", binding), Map.of(), "r", "owner", Instant.now(), "b".repeat(64));
        var actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot("SET", Map.of(), Map.of("primary", "OTHER"));
        var inventory = new FigmaLibraryInventorySnapshot("krds", "3", "i1", Instant.now(), Map.of("SET", actual));

        var issues = new ComponentRegistryInventoryValidator().validate(registry, inventory);

        assertThat(issues).extracting(issue -> issue.code()).contains("PUBLISHED_VARIANT_NOT_IN_INVENTORY");
    }

    @Test
    void detectsPropertyTypeDriftAgainstInventory() {
        var binding = new ComponentRegistrySnapshotV3.Binding("SET", "Button",
                ComponentRegistryEntry.PublishStatus.CURRENT, ComponentRegistryEntry.LifecycleStatus.CURRENT, Map.of());
        var registry = new ComponentRegistrySnapshotV3("component-registry-v3", "krds", "2", "3", "2",
                "a".repeat(64), null, Map.of("button", binding), Map.of(), "r", "owner", Instant.now(), "b".repeat(64));
        var actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot("SET",
                Map.of("Label", new FigmaPropertyDriftValidator.ActualProperty("BOOLEAN", java.util.Set.of())), Map.of());
        var inventory = new FigmaLibraryInventorySnapshot("krds", "3", "i1", Instant.now(), Map.of("SET", actual));
        var property = new ComponentCatalog.Property(ComponentRegistryEntry.PropertyType.TEXT, "Label", "button.text", Map.of());
        var contract = new ComponentCatalog.Entry(ComponentCatalog.Kind.COMPONENT, ComponentCatalog.Requirement.REQUIRED,
                java.util.List.of(), null, Map.of("label", property), java.util.List.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), null, null);
        var catalog = new ComponentCatalog("component-catalog-v2", "2", Map.of("button", contract), null);

        var issues = new ComponentRegistryInventoryValidator().validate(registry, inventory, catalog);

        assertThat(issues).extracting(issue -> issue.code()).contains("PUBLISHED_PROPERTY_NOT_IN_INVENTORY");
    }
}
