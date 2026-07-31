package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRegistryResolverTest {

    private final ComponentRegistryResolver resolver = new ComponentRegistryResolver();

    @Test
    void aliasOfDeprecatedComponentResolvesToActiveReplacement() {
        ComponentRegistry registry = registry(Map.of(
                "krds.button", entry("OLD_KEY", ComponentRegistryEntry.LifecycleStatus.DEPRECATED,
                        "krds.action-button", List.of("egov.button")),
                "krds.action-button", entry("NEW_KEY", ComponentRegistryEntry.LifecycleStatus.ACTIVE,
                        null, List.of())
        ));

        ComponentRegistryResolver.Resolution resolution = resolver.resolve(registry, "egov.button");

        assertThat(resolution.resolved()).isTrue();
        assertThat(resolution.kind()).isEqualTo(ComponentRegistryResolver.ResolutionKind.REPLACEMENT);
        assertThat(resolution.resolvedLogicalType()).isEqualTo("krds.action-button");
        assertThat(resolution.entry().componentSetKey()).isEqualTo("NEW_KEY");
        assertThat(resolution.path()).containsExactly("egov.button", "krds.button", "krds.action-button");
    }

    @Test
    void replacementCycleIsReportedWithoutReturningAKey() {
        ComponentRegistry registry = registry(Map.of(
                "krds.a", entry("A_KEY", ComponentRegistryEntry.LifecycleStatus.DEPRECATED,
                        "krds.b", List.of()),
                "krds.b", entry("B_KEY", ComponentRegistryEntry.LifecycleStatus.DEPRECATED,
                        "krds.a", List.of())
        ));

        ComponentRegistryResolver.Resolution resolution = resolver.resolve(registry, "krds.a");

        assertThat(resolution.resolved()).isFalse();
        assertThat(resolution.kind()).isEqualTo(ComponentRegistryResolver.ResolutionKind.CYCLE);
    }

    private ComponentRegistry registry(Map<String, ComponentRegistryEntry> components) {
        return new ComponentRegistry(
                "ftc-krds",
                "1.0.0",
                "registry-2",
                new ComponentRegistry.LibraryRef("FILE_KEY", "FTC Library"),
                components,
                Map.of());
    }

    private ComponentRegistryEntry entry(
            String key,
            ComponentRegistryEntry.LifecycleStatus lifecycle,
            String replacement,
            List<String> aliases
    ) {
        return new ComponentRegistryEntry(
                key,
                key,
                ComponentRegistryEntry.PublishStatus.CURRENT,
                lifecycle,
                replacement,
                aliases,
                Map.of("Default", key + "_DEFAULT"),
                Map.of());
    }
}
