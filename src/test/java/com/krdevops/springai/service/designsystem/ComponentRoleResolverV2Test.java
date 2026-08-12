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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
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

    /** KRV-035: Registry Map 삽입 순서를 무작위로 바꿔도 유일 후보 해석 결과가 항상 동일해야 한다. */
    @Test
    void resolvedLogicalTypeIsDeterministicAcrossShuffledInsertionOrder() {
        List<String> distractors = List.of("krds.button.b", "krds.button.c", "krds.button.d", "krds.button.e");
        Set<String> results = new java.util.HashSet<>();
        for (int seed = 0; seed < 30; seed++) {
            List<String> order = new ArrayList<>(distractors);
            order.add("krds.button.a");
            Collections.shuffle(order, new Random(seed));

            LinkedHashMap<String, ComponentRegistryEntry> entries = new LinkedHashMap<>();
            for (String logicalType : order) {
                boolean isTarget = logicalType.equals("krds.button.a");
                entries.put(logicalType, entry(logicalType.toUpperCase(Locale.ROOT),
                        isTarget ? Set.of(SemanticRole.ACTION_PRIMARY) : Set.of(SemanticRole.ACTION_SECONDARY)));
            }

            ComponentRoleResolver.Resolution result = resolver.resolve(registry(entries), context());
            assertThat(result.resolved()).isTrue();
            results.add(result.logicalType());
        }
        assertThat(results).containsExactly("krds.button.a");
    }

    /** KRV-035: 복수 후보 Ambiguous 케이스도 삽입 순서와 무관하게 동일한(정렬된) 후보 목록을 반환해야 한다. */
    @Test
    void ambiguousCandidateOrderIsDeterministicAcrossShuffledInsertionOrder() {
        List<String> logicalTypes = List.of("krds.button.a", "krds.button.b", "krds.button.c");
        for (int seed = 0; seed < 30; seed++) {
            List<String> order = new ArrayList<>(logicalTypes);
            Collections.shuffle(order, new Random(seed));

            LinkedHashMap<String, ComponentRegistryEntry> entries = new LinkedHashMap<>();
            for (String logicalType : order) {
                entries.put(logicalType, entry(logicalType.toUpperCase(Locale.ROOT), Set.of(SemanticRole.ACTION_PRIMARY)));
            }

            ComponentRoleResolver.Resolution result = resolver.resolve(registry(entries), context());
            assertThat(result.errorCode()).isEqualTo("ROLE_AMBIGUOUS");
            assertThat(result.candidates()).extracting(ComponentRoleResolver.Candidate::logicalType)
                    .containsExactly("krds.button.a", "krds.button.b", "krds.button.c");
        }
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
