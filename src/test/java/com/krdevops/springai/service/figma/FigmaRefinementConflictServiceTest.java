package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementConflictStatus;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementOwner;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatch;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPropertyType;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** MR-T04/T05: baseMaterializationHash 불일치, 대상 노드 삭제/타입 변경, upstream 변경 충돌 판정 검증. */
class FigmaRefinementConflictServiceTest {

    private final FigmaRefinementConflictService service = new FigmaRefinementConflictService();

    @Test
    void baseHashMismatchIsClassifiedAsBaseStale() {
        FigmaRefinementPatch patch = patch("n1", "krds.detailRow", "width", 160, 176);
        FigmaNodeSpec tree = leaf("n1", "krds.detailRow");

        assertThat(service.classify(patch, tree, false)).isEqualTo(FigmaRefinementConflictStatus.BASE_STALE);
    }

    @Test
    void missingTargetNodeIsClassifiedAsTargetRemoved() {
        FigmaRefinementPatch patch = patch("n1", "krds.detailRow", "width", 160, 176);
        FigmaNodeSpec tree = leaf("other-node", "krds.detailRow");

        assertThat(service.classify(patch, tree, true)).isEqualTo(FigmaRefinementConflictStatus.TARGET_REMOVED);
    }

    @Test
    void changedLogicalTypeIsClassifiedAsTypeChanged() {
        FigmaRefinementPatch patch = patch("n1", "krds.detailRow", "width", 160, 176);
        FigmaNodeSpec tree = leaf("n1", "krds.button");

        assertThat(service.classify(patch, tree, true)).isEqualTo(FigmaRefinementConflictStatus.TYPE_CHANGED);
    }

    @Test
    void upstreamChangedValueIsClassifiedAsUpstreamChanged() {
        FigmaRefinementPatch patch = patch("n1", "krds.detailRow", "width", 160, 176);
        FigmaNodeSpec tree = leafWithProperty("n1", "krds.detailRow", "width", 200);

        assertThat(service.classify(patch, tree, true)).isEqualTo(FigmaRefinementConflictStatus.UPSTREAM_CHANGED);
    }

    @Test
    void matchingBaselineValueIsClassifiedAsNone() {
        FigmaRefinementPatch patch = patch("n1", "krds.detailRow", "width", 160, 176);
        FigmaNodeSpec tree = leafWithProperty("n1", "krds.detailRow", "width", 160);

        assertThat(service.classify(patch, tree, true)).isEqualTo(FigmaRefinementConflictStatus.NONE);
    }

    @Test
    void systemLayoutOwnerIsAlwaysPolicyBlockedRegardlessOfTreeState() {
        FigmaRefinementPatch patch = new FigmaRefinementPatch(
                "n1", "krds.detailRow", "layoutMode", FigmaRefinementPropertyType.STRING,
                "VERTICAL", "HORIZONTAL", FigmaRefinementOwner.SYSTEM_LAYOUT, FigmaRefinementScope.ALLOWED,
                FigmaRefinementConflictStatus.NONE);
        FigmaNodeSpec tree = leaf("n1", "krds.detailRow");

        assertThat(service.classify(patch, tree, true)).isEqualTo(FigmaRefinementConflictStatus.POLICY_BLOCKED);
    }

    @Test
    void blockedScopeIsAlwaysPolicyBlocked() {
        FigmaRefinementPatch patch = new FigmaRefinementPatch(
                "n1", "krds.detailRow", "visible", FigmaRefinementPropertyType.BOOLEAN,
                true, false, FigmaRefinementOwner.MANUAL_REFINEMENT, FigmaRefinementScope.BLOCKED,
                FigmaRefinementConflictStatus.NONE);
        FigmaNodeSpec tree = leaf("n1", "krds.detailRow");

        assertThat(service.classify(patch, tree, true)).isEqualTo(FigmaRefinementConflictStatus.POLICY_BLOCKED);
    }

    @Test
    void reclassifyReturnsNewPatchWithUpdatedConflictStatus() {
        FigmaRefinementPatch patch = patch("n1", "krds.detailRow", "width", 160, 176);
        FigmaNodeSpec tree = leaf("other-node", "krds.detailRow");

        FigmaRefinementPatch reclassified = service.reclassify(patch, tree, true);

        assertThat(reclassified.conflictStatus()).isEqualTo(FigmaRefinementConflictStatus.TARGET_REMOVED);
        assertThat(reclassified.logicalNodeId()).isEqualTo(patch.logicalNodeId());
        assertThat(reclassified.propertyPath()).isEqualTo(patch.propertyPath());
    }

    @Test
    void findsNestedTargetNodeInTree() {
        FigmaRefinementPatch patch = patch("child", "krds.button", "opacity", 1, 0.5);
        FigmaNodeSpec child = new FigmaNodeSpec("child", FigmaNodeSpec.NodeType.COMPONENT, "krds.button",
                Map.of("opacity", 1), resolvedRef(), List.of());
        FigmaNodeSpec root = new FigmaNodeSpec("root", FigmaNodeSpec.NodeType.SECTION, "egov.actionArea",
                Map.of(), null, List.of(child));

        assertThat(service.classify(patch, root, true)).isEqualTo(FigmaRefinementConflictStatus.NONE);
    }

    private FigmaRefinementPatch patch(
            String logicalNodeId, String baselineLogicalType, String propertyPath, Object before, Object after) {
        return new FigmaRefinementPatch(logicalNodeId, baselineLogicalType, propertyPath,
                FigmaRefinementPropertyType.NUMBER, before, after,
                FigmaRefinementOwner.MANUAL_REFINEMENT, FigmaRefinementScope.CONDITIONAL,
                FigmaRefinementConflictStatus.NONE);
    }

    private FigmaNodeSpec leaf(String logicalNodeId, String logicalType) {
        return new FigmaNodeSpec(logicalNodeId, FigmaNodeSpec.NodeType.COMPONENT, logicalType,
                Map.of(), null, List.of());
    }

    private FigmaNodeSpec leafWithProperty(String logicalNodeId, String logicalType, String key, Object value) {
        return new FigmaNodeSpec(logicalNodeId, FigmaNodeSpec.NodeType.COMPONENT, logicalType,
                Map.of(key, value), null, List.of());
    }

    private ResolvedComponentRef resolvedRef() {
        return new ResolvedComponentRef(SemanticRole.ACTION_PRIMARY, "krds.button", "BUTTON_SET", "PRIMARY_KEY",
                Map.of(), Map.of(), "1.0.0", "1.0.0", null, "hash");
    }
}
