package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.role.Platform;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.figma.ComponentResolutionContext;
import com.krdevops.springai.model.figma.FigmaScreenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRoleResolverV2Test {
    private final ComponentRoleResolver resolver = new ComponentRoleResolver();

    @Test
    void resolvesExactlyOneCurrentDesktopContract() {
        ComponentRegistry registry = registry(Map.of("krds.button", entry("KEY", Set.of(SemanticRole.ACTION_PRIMARY))));
        ComponentRoleResolver.Resolution result = resolver.resolve(registry, context());
        assertThat(result.resolved()).isTrue();
        assertThat(result.logicalType()).isEqualTo("krds.button");
    }

    @Test
    void twoCurrentContractsAreAmbiguousRegardlessOfMapOrder() {
        ComponentRegistry registry = registry(Map.of(
                "krds.button.a", entry("A", Set.of(SemanticRole.ACTION_PRIMARY)),
                "krds.button.b", entry("B", Set.of(SemanticRole.ACTION_PRIMARY))));
        ComponentRoleResolver.Resolution result = resolver.resolve(registry, context());
        assertThat(result.resolved()).isFalse();
        assertThat(result.errorCode()).isEqualTo("ROLE_AMBIGUOUS");
        assertThat(result.candidates()).extracting(ComponentRoleResolver.Candidate::logicalType)
                .containsExactly("krds.button.a", "krds.button.b");
    }

    private ComponentResolutionContext context() {
        return new ComponentResolutionContext(ScreenPattern.CRUD_CREATE, FigmaScreenType.FORM,
                Platform.DESKTOP, LayoutDensity.STANDARD, null, null,
                null, null, null, SemanticRole.ACTION_PRIMARY);
    }

    private ComponentRegistry registry(Map<String, ComponentRegistryEntry> entries) {
        return new ComponentRegistry("krds", "2.0.0", "2.0.0", null, entries, Map.of());
    }

    private ComponentRegistryEntry entry(String key, Set<SemanticRole> roles) {
        return new ComponentRegistryEntry(key, "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(), Map.of(), Map.of(),
                roles, Set.of(Platform.DESKTOP), Map.of(), Set.of(), null, null, "2.0.0");
    }
}
