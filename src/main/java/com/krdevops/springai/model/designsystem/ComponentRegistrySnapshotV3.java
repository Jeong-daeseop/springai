package com.krdevops.springai.model.designsystem;

import java.time.Instant;
import java.util.Map;

/** Catalog 논리 계약과 분리된 Published Figma Binding 불변 Snapshot. */
public record ComponentRegistrySnapshotV3(
        String schemaVersion,
        String profileId,
        String profileVersion,
        String registryVersion,
        String catalogVersion,
        String catalogHash,
        ComponentRegistry.LibraryRef library,
        Map<String, Binding> bindings,
        Map<String, Object> variables,
        String sourceRevision,
        String approvedBy,
        Instant approvedAt,
        String contentHash
) {
    public static final String SCHEMA_VERSION = "component-registry-v3";

    public ComponentRegistrySnapshotV3 {
        bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    public boolean approved() {
        return approvedBy != null && !approvedBy.isBlank() && approvedAt != null;
    }

    public record Binding(
            String componentSetKey,
            String componentName,
            ComponentRegistryEntry.PublishStatus publishStatus,
            ComponentRegistryEntry.LifecycleStatus lifecycleStatus,
            Map<String, String> variants
    ) {
        public Binding {
            variants = variants == null ? Map.of() : Map.copyOf(variants);
        }

        public boolean currentForGeneration() {
            return publishStatus == ComponentRegistryEntry.PublishStatus.CURRENT
                    && lifecycleStatus == ComponentRegistryEntry.LifecycleStatus.CURRENT;
        }
    }
}
