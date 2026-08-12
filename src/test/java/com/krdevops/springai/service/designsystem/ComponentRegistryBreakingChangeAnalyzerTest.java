package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.design.role.Platform;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.VariantAxisDefinition;
import com.krdevops.springai.model.designsystem.VariantRule;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** KRV-072: Registry/Rule Set 버전 간 Breaking Change(Role/Axis 제거, Property 이름 변경, Rule 결과 변경) 감지 검증. */
class ComponentRegistryBreakingChangeAnalyzerTest {

    private final ComponentRegistryBreakingChangeAnalyzer analyzer = new ComponentRegistryBreakingChangeAnalyzer();

    @Test
    void removedRoleIsDetected() {
        ComponentRegistry previous = registry(entry(
                Set.of(SemanticRole.ACTION_PRIMARY, SemanticRole.ACTION_SECONDARY), Map.of()));
        ComponentRegistry candidate = registry(entry(Set.of(SemanticRole.ACTION_PRIMARY), Map.of()));

        List<ComponentRegistryBreakingChangeAnalyzer.BreakingChange> changes =
                analyzer.analyzeRegistry(previous, candidate);

        assertThat(changes).anySatisfy(change -> {
            assertThat(change.type()).isEqualTo(ComponentRegistryBreakingChangeAnalyzer.ChangeType.ROLE_REMOVED);
            assertThat(change.detail()).isEqualTo(SemanticRole.ACTION_SECONDARY.code());
        });
    }

    @Test
    void removedAxisIsDetected() {
        VariantAxisDefinition axis = new VariantAxisDefinition("style", "Style", Set.of("Primary"), true);
        ComponentRegistry previous = registry(entry(Set.of(), Map.of("style", axis)));
        ComponentRegistry candidate = registry(entry(Set.of(), Map.of()));

        List<ComponentRegistryBreakingChangeAnalyzer.BreakingChange> changes =
                analyzer.analyzeRegistry(previous, candidate);

        assertThat(changes).extracting(ComponentRegistryBreakingChangeAnalyzer.BreakingChange::type)
                .contains(ComponentRegistryBreakingChangeAnalyzer.ChangeType.AXIS_REMOVED);
    }

    @Test
    void renamedFigmaPropertyIsDetected() {
        ComponentRegistryEntry before = new ComponentRegistryEntry(
                "BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(), Map.of(),
                Map.of("style", new ComponentRegistryEntry.PropertyMapping(
                        "Style", ComponentRegistryEntry.PropertyType.VARIANT, Map.of())),
                Set.of(), Set.of(), Map.of(), Set.of(), null, null, "1.0.0");
        ComponentRegistryEntry after = new ComponentRegistryEntry(
                "BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(), Map.of(),
                Map.of("style", new ComponentRegistryEntry.PropertyMapping(
                        "Variant", ComponentRegistryEntry.PropertyType.VARIANT, Map.of())),
                Set.of(), Set.of(), Map.of(), Set.of(), null, null, "2.0.0");

        List<ComponentRegistryBreakingChangeAnalyzer.BreakingChange> changes =
                analyzer.analyzeRegistry(registry(before), registry(after));

        assertThat(changes).extracting(ComponentRegistryBreakingChangeAnalyzer.BreakingChange::type)
                .contains(ComponentRegistryBreakingChangeAnalyzer.ChangeType.PROPERTY_RENAMED);
    }

    @Test
    void unchangedRegistryProducesNoBreakingChanges() {
        ComponentRegistryEntry entry = entry(Set.of(SemanticRole.ACTION_PRIMARY), Map.of());
        List<ComponentRegistryBreakingChangeAnalyzer.BreakingChange> changes =
                analyzer.analyzeRegistry(registry(entry), registry(entry));

        assertThat(changes).isEmpty();
    }

    @Test
    void removedRuleAndChangedRuleResultAreDetected() {
        VariantRule keptButChanged = rule("rule-1", Map.of("style", "primary"));
        VariantRule removed = rule("rule-2", Map.of("style", "secondary"));
        VariantRuleSet previous = ruleSet(List.of(keptButChanged, removed));

        VariantRule changedResult = rule("rule-1", Map.of("style", "primary-updated"));
        VariantRuleSet candidate = ruleSet(List.of(changedResult));

        List<ComponentRegistryBreakingChangeAnalyzer.BreakingChange> changes =
                analyzer.analyzeRuleSet(previous, candidate);

        assertThat(changes).extracting(ComponentRegistryBreakingChangeAnalyzer.BreakingChange::type)
                .containsExactlyInAnyOrder(
                        ComponentRegistryBreakingChangeAnalyzer.ChangeType.RULE_RESULT_CHANGED,
                        ComponentRegistryBreakingChangeAnalyzer.ChangeType.RULE_REMOVED);
    }

    private VariantRule rule(String ruleId, Map<String, String> result) {
        return new VariantRule(ruleId, 10, SemanticRole.ACTION_PRIMARY, Map.of("pattern", "crud.create"), result);
    }

    private VariantRuleSet ruleSet(List<VariantRule> rules) {
        return new VariantRuleSet("krds", "1.0.0", "krds", "2.0.0", VariantRuleSet.Status.PUBLISHED, rules);
    }

    private ComponentRegistryEntry entry(Set<SemanticRole> roles, Map<String, VariantAxisDefinition> axes) {
        return new ComponentRegistryEntry(
                "BUTTON_SET", "Button", ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(), Map.of(), Map.of(),
                roles, Set.of(Platform.DESKTOP), axes, Set.of(), null, null, "1.0.0");
    }

    private ComponentRegistry registry(ComponentRegistryEntry entry) {
        return new ComponentRegistry("krds", "1.0.0", "2.0.0", null, Map.of("krds.button", entry), Map.of());
    }
}
