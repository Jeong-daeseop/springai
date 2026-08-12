package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.Platform;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.VariantAxisDefinition;
import com.krdevops.springai.model.designsystem.VariantRule;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.ComponentResolutionContext;
import com.krdevops.springai.model.figma.FigmaScreenType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class VariantRuleResolverTest {
    private final VariantRuleResolver resolver = new VariantRuleResolver();

    @Test
    void exactContextResolvesOnePublishedVariant() {
        VariantRuleResolver.Resolution result = resolver.resolve(contract(), context(), rules(List.of(rule("primary", 10))));
        assertThat(result.resolved()).isTrue();
        assertThat(result.variantKey()).isEqualTo("PRIMARY_KEY");
        assertThat(result.variantProperties()).containsExactlyInAnyOrderEntriesOf(
                Map.of("Style", "Primary", "Size", "Medium"));
    }

    @Test
    void sameSpecificityAndPriorityIsBlockedAsAmbiguous() {
        VariantRuleResolver.Resolution result = resolver.resolve(contract(), context(),
                rules(List.of(rule("a", 10), rule("b", 10))));
        assertThat(result.resolved()).isFalse();
        assertThat(result.errorCode()).isEqualTo("VARIANT_RULE_AMBIGUOUS");
    }

    @Test
    void noRuleNeverFallsBackToFirstVariant() {
        VariantRuleResolver.Resolution result = resolver.resolve(contract(), context(), rules(List.of()));
        assertThat(result.resolved()).isFalse();
        assertThat(result.errorCode()).isEqualTo("VARIANT_RULE_NOT_FOUND");
    }

    private ComponentResolutionContext context() {
        return new ComponentResolutionContext(ScreenPattern.CRUD_CREATE, FigmaScreenType.FORM,
                Platform.DESKTOP, LayoutDensity.STANDARD, null, ComponentState.DEFAULT,
                null, false, null, SemanticRole.ACTION_PRIMARY);
    }

    private VariantRule rule(String id, int priority) {
        return new VariantRule(id, priority, SemanticRole.ACTION_PRIMARY,
                Map.of("pattern", "crud.create", "platform", "DESKTOP"),
                Map.of("style", "primary", "size", "medium"));
    }

    private VariantRuleSet rules(List<VariantRule> rules) {
        return new VariantRuleSet("krds", "1.0.0", "krds", "2.0.0",
                VariantRuleSet.Status.PUBLISHED, rules);
    }

    private ComponentRegistryEntry contract() {
        Map<String, ComponentRegistryEntry.PropertyMapping> properties = Map.of(
                "style", new ComponentRegistryEntry.PropertyMapping("Style",
                        ComponentRegistryEntry.PropertyType.VARIANT, Map.of("primary", "Primary")),
                "size", new ComponentRegistryEntry.PropertyMapping("Size",
                        ComponentRegistryEntry.PropertyType.VARIANT, Map.of("medium", "Medium")));
        Map<String, VariantAxisDefinition> axes = Map.of(
                "style", new VariantAxisDefinition("style", "Style", Set.of("Primary"), true),
                "size", new VariantAxisDefinition("size", "Size", Set.of("Medium"), true));
        return new ComponentRegistryEntry("BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(),
                Map.of("Style=Primary, Size=Medium", "PRIMARY_KEY"), properties,
                Set.of(SemanticRole.ACTION_PRIMARY), Set.of(Platform.DESKTOP), axes,
                Set.of("style", "size"), null, null, "2.0.0");
    }
}
