package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.model.design.role.ScreenPattern;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** KRV-049 Shadow Mode: 두 해석 결과 트리를 비교해 화면·노드·Rule ID 단위 차이를 보고하는지 검증한다. */
class ComponentResolutionShadowComparatorTest {

    private final ComponentResolutionShadowComparator comparator = new ComponentResolutionShadowComparator();

    @Test
    void identicalResolutionsProduceNoDifferences() {
        var baseline = result(button("BUTTON_SET", "PRIMARY_KEY", "rule-1"));
        var candidate = result(button("BUTTON_SET", "PRIMARY_KEY", "rule-1"));

        var comparison = comparator.compare("screen-1", baseline, candidate);

        assertThat(comparison.identical()).isTrue();
        assertThat(comparison.differences()).isEmpty();
    }

    @Test
    void differentComponentSetKeyIsReportedAsComponentResolutionChanged() {
        var baseline = result(button("BUTTON_SET_V1", "PRIMARY_KEY", "rule-1"));
        var candidate = result(button("BUTTON_SET_V2", "PRIMARY_KEY", "rule-1"));

        var comparison = comparator.compare("screen-1", baseline, candidate);

        assertThat(comparison.identical()).isFalse();
        assertThat(comparison.differences()).extracting(ComponentResolutionShadowComparator.NodeDifference::type)
                .containsExactly(ComponentResolutionShadowComparator.DifferenceType.COMPONENT_RESOLUTION_CHANGED);
        assertThat(comparison.differences().get(0).logicalNodeId()).isEqualTo("action-1");
    }

    @Test
    void sameComponentButDifferentRuleIdIsReportedAsRuleIdChanged() {
        var baseline = result(button("BUTTON_SET", "PRIMARY_KEY", "rule-1"));
        var candidate = result(button("BUTTON_SET", "PRIMARY_KEY", "rule-2"));

        var comparison = comparator.compare("screen-1", baseline, candidate);

        assertThat(comparison.differences()).extracting(ComponentResolutionShadowComparator.NodeDifference::type)
                .containsExactly(ComponentResolutionShadowComparator.DifferenceType.RULE_ID_CHANGED);
        assertThat(comparison.differences().get(0).before()).isEqualTo("rule-1");
        assertThat(comparison.differences().get(0).after()).isEqualTo("rule-2");
    }

    @Test
    void differentPatternIsReportedAsPatternChanged() {
        var baseline = new KrdsComponentResolutionService.ResolutionResult(
                button("BUTTON_SET", "PRIMARY_KEY", "rule-1"), ScreenPattern.CRUD_CREATE, "v1", "v1", "v1");
        var candidate = new KrdsComponentResolutionService.ResolutionResult(
                button("BUTTON_SET", "PRIMARY_KEY", "rule-1"), ScreenPattern.CRUD_EDIT, "v1", "v1", "v1");

        var comparison = comparator.compare("screen-1", baseline, candidate);

        assertThat(comparison.differences()).extracting(ComponentResolutionShadowComparator.NodeDifference::type)
                .contains(ComponentResolutionShadowComparator.DifferenceType.PATTERN_CHANGED);
    }

    @Test
    void missingNodeInCandidateIsReportedAsStructureChanged() {
        FigmaNodeSpec baselineRoot = new FigmaNodeSpec("root", FigmaNodeSpec.NodeType.SECTION, "egov.actionArea",
                Map.of(), null, List.of(button("BUTTON_SET", "PRIMARY_KEY", "rule-1")));
        FigmaNodeSpec candidateRoot = new FigmaNodeSpec("root", FigmaNodeSpec.NodeType.SECTION, "egov.actionArea",
                Map.of(), null, List.of());

        var comparison = comparator.compare("screen-1",
                new KrdsComponentResolutionService.ResolutionResult(baselineRoot, ScreenPattern.CRUD_CREATE, "v1", "v1", "v1"),
                new KrdsComponentResolutionService.ResolutionResult(candidateRoot, ScreenPattern.CRUD_CREATE, "v1", "v1", "v1"));

        assertThat(comparison.differences()).extracting(ComponentResolutionShadowComparator.NodeDifference::type)
                .containsExactly(ComponentResolutionShadowComparator.DifferenceType.NODE_STRUCTURE_CHANGED);
    }

    private KrdsComponentResolutionService.ResolutionResult result(FigmaNodeSpec content) {
        return new KrdsComponentResolutionService.ResolutionResult(content, ScreenPattern.CRUD_CREATE, "v1", "v1", "v1");
    }

    private FigmaNodeSpec button(String componentSetKey, String variantKey, String ruleId) {
        ResolvedComponentRef ref = new ResolvedComponentRef(
                SemanticRole.ACTION_PRIMARY, "krds.button", componentSetKey, variantKey,
                Map.of(), Map.of(), "2.0.0", "1.0.0", ruleId, "hash");
        return new FigmaNodeSpec("action-1", FigmaNodeSpec.NodeType.COMPONENT, "krds.button",
                Map.of(), ref, List.of());
    }
}
