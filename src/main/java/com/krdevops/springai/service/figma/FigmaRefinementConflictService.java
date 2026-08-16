package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementConflictStatus;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementOwner;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementPatch;
import com.krdevops.springai.model.figma.refinement.FigmaRefinementScope;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * MR-S07: 새로 Materialize된 화면 트리(Refinement 적용 전 base bundle)를 기준으로 승인된
 * Patch 각각이 재적용 가능한지 판정한다. 순수 함수 스타일로 구현해 Figma API나 DB 접근 없이
 * 단위 테스트 가능하게 유지한다.
 */
@Service
public class FigmaRefinementConflictService {

    /**
     * @param patch          재적용 후보 Patch(승인된 값)
     * @param newTree        새로 Materialize된 화면의 semantic 트리(Refinement 미적용)
     * @param baseHashMatches Patch Set의 baseMaterializationHash가 현재 화면 상태와 일치하는지 여부
     */
    public FigmaRefinementConflictStatus classify(
            FigmaRefinementPatch patch, FigmaNodeSpec newTree, boolean baseHashMatches) {
        if (patch.owner() == FigmaRefinementOwner.SYSTEM_LAYOUT || patch.scope() == FigmaRefinementScope.BLOCKED) {
            return FigmaRefinementConflictStatus.POLICY_BLOCKED;
        }
        if (!baseHashMatches) {
            return FigmaRefinementConflictStatus.BASE_STALE;
        }
        FigmaNodeSpec target = findNode(newTree, patch.logicalNodeId());
        if (target == null) {
            return FigmaRefinementConflictStatus.TARGET_REMOVED;
        }
        if (!Objects.equals(target.type(), patch.baselineLogicalType())) {
            return FigmaRefinementConflictStatus.TYPE_CHANGED;
        }
        Object currentValue = target.properties().get(patch.propertyPath());
        if (currentValue != null && !valuesEqual(currentValue, patch.before())) {
            return FigmaRefinementConflictStatus.UPSTREAM_CHANGED;
        }
        return FigmaRefinementConflictStatus.NONE;
    }

    /** 트리 전체를 순회하며 각 Patch의 최신 충돌 상태로 갱신한 새 Patch를 반환한다. */
    public FigmaRefinementPatch reclassify(FigmaRefinementPatch patch, FigmaNodeSpec newTree, boolean baseHashMatches) {
        FigmaRefinementConflictStatus status = classify(patch, newTree, baseHashMatches);
        if (status == patch.conflictStatus()) return patch;
        return new FigmaRefinementPatch(patch.logicalNodeId(), patch.baselineLogicalType(), patch.propertyPath(),
                patch.propertyType(), patch.before(), patch.after(), patch.owner(), patch.scope(), status);
    }

    private FigmaNodeSpec findNode(FigmaNodeSpec root, String logicalNodeId) {
        if (root == null) return null;
        Map<String, FigmaNodeSpec> index = new HashMap<>();
        indexNodes(root, index);
        return index.get(logicalNodeId);
    }

    private void indexNodes(FigmaNodeSpec node, Map<String, FigmaNodeSpec> index) {
        index.put(node.logicalNodeId(), node);
        node.children().forEach(child -> indexNodes(child, index));
    }

    private boolean valuesEqual(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Math.abs(leftNumber.doubleValue() - rightNumber.doubleValue()) < 0.0001;
        }
        return Objects.equals(left, right);
    }
}
