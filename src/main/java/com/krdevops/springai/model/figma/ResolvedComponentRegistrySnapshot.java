package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/** 서버가 Catalog 계약과 Registry v3 Binding을 결합해 Plugin에 전달하는 실행 전용 투영. */
public record ResolvedComponentRegistrySnapshot(
        String profileId,
        String profileVersion,
        String registryVersion,
        String catalogVersion,
        String catalogHash,
        String registryHash,
        Map<String, Entry> components
) {
    public ResolvedComponentRegistrySnapshot {
        components = components == null ? Map.of() : Map.copyOf(components);
    }

    public static ResolvedComponentRegistrySnapshot from(ResolvedComponentRegistry resolved) {
        Map<String, Entry> components = new LinkedHashMap<>();
        resolved.entries().values().forEach(entry -> entry.atomicBindings().forEach(atomic -> {
            Map<String, ComponentRegistryEntry.PropertyMapping> properties = new LinkedHashMap<>();
            atomic.contract().properties().forEach((name, property) -> properties.put(name,
                    new ComponentRegistryEntry.PropertyMapping(
                            property.figmaProperty(), property.type(), property.values())));
            components.putIfAbsent(atomic.logicalType(), new Entry(
                    atomic.binding().componentSetKey(), atomic.binding().componentName(),
                    atomic.binding().variants(), properties));
        }));
        return new ResolvedComponentRegistrySnapshot(
                resolved.profileId(), resolved.profileVersion(), resolved.registryVersion(),
                resolved.catalogVersion(), resolved.catalogHash(), resolved.registryHash(), components);
    }

    public record Entry(
            String componentSetKey,
            String componentName,
            Map<String, String> variants,
            Map<String, ComponentRegistryEntry.PropertyMapping> properties
    ) {
        public Entry {
            variants = variants == null ? Map.of() : Map.copyOf(variants);
            properties = properties == null ? Map.of() : Map.copyOf(properties);
        }
    }
}
