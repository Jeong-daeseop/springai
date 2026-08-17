package com.krdevops.springai.model.designsystem;

import java.util.List;
import java.util.Map;

/** Catalog 계약과 Registry Binding을 정확한 버전으로 결합한 Materialization 전용 읽기 모델. */
public record ResolvedComponentRegistry(
        String catalogVersion,
        String catalogHash,
        String profileId,
        String profileVersion,
        String registryVersion,
        String registryHash,
        Map<String, ResolvedEntry> entries
) {
    public ResolvedComponentRegistry {
        entries = entries == null ? Map.of() : Map.copyOf(entries);
    }

    public record ResolvedEntry(
            String requestedLogicalType,
            String canonicalLogicalType,
            ComponentCatalog.Entry contract,
            List<AtomicBinding> atomicBindings,
            List<String> resolutionPath
    ) {
        public ResolvedEntry {
            atomicBindings = atomicBindings == null ? List.of() : List.copyOf(atomicBindings);
            resolutionPath = resolutionPath == null ? List.of() : List.copyOf(resolutionPath);
        }
    }

    public record AtomicBinding(
            String logicalType,
            ComponentCatalog.Entry contract,
            ComponentRegistrySnapshotV3.Binding binding
    ) {}
}
