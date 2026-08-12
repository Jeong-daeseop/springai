package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * KRV-049 Shadow Mode: 서로 다른 Registry/Rule Set Version으로 두 번 해석한
 * {@link KrdsComponentResolutionService.ResolutionResult}를 비교해 Apply 없이 차이만 보고한다.
 * 두 결과를 얻는 책임(예: 현재 Published 조합과 아직 승인되지 않은 후보 조합으로 각각
 * {@code KrdsComponentResolutionService.resolve}를 호출하는 것)은 호출자에게 있다.
 */
@Component
public class ComponentResolutionShadowComparator {

    public ShadowComparisonResult compare(
            String screenId,
            KrdsComponentResolutionService.ResolutionResult baseline,
            KrdsComponentResolutionService.ResolutionResult candidate
    ) {
        List<NodeDifference> differences = new ArrayList<>();
        if (!Objects.equals(baseline.pattern(), candidate.pattern())) {
            differences.add(new NodeDifference(screenId, null, DifferenceType.PATTERN_CHANGED,
                    String.valueOf(baseline.pattern()), String.valueOf(candidate.pattern())));
        }
        compareNode(screenId, baseline.content(), candidate.content(), differences);
        return new ShadowComparisonResult(screenId, List.copyOf(differences));
    }

    private void compareNode(
            String screenId, FigmaNodeSpec before, FigmaNodeSpec after, List<NodeDifference> differences) {
        if (before == null && after == null) {
            return;
        }
        if (before == null || after == null || !before.logicalNodeId().equals(after.logicalNodeId())) {
            String nodeId = before != null ? before.logicalNodeId() : after.logicalNodeId();
            differences.add(new NodeDifference(screenId, nodeId, DifferenceType.NODE_STRUCTURE_CHANGED,
                    before == null ? null : "present", after == null ? null : "present"));
            return;
        }
        compareResolution(screenId, before, after, differences);
        int childCount = Math.max(before.children().size(), after.children().size());
        for (int i = 0; i < childCount; i++) {
            FigmaNodeSpec childBefore = i < before.children().size() ? before.children().get(i) : null;
            FigmaNodeSpec childAfter = i < after.children().size() ? after.children().get(i) : null;
            compareNode(screenId, childBefore, childAfter, differences);
        }
    }

    private void compareResolution(
            String screenId, FigmaNodeSpec before, FigmaNodeSpec after, List<NodeDifference> differences) {
        ResolvedComponentRef beforeRef = before.componentResolution();
        ResolvedComponentRef afterRef = after.componentResolution();
        if (beforeRef == null && afterRef == null) {
            return;
        }
        if (beforeRef == null || afterRef == null
                || !Objects.equals(beforeRef.componentSetKey(), afterRef.componentSetKey())
                || !Objects.equals(beforeRef.variantKey(), afterRef.variantKey())) {
            differences.add(new NodeDifference(screenId, before.logicalNodeId(), DifferenceType.COMPONENT_RESOLUTION_CHANGED,
                    describe(beforeRef), describe(afterRef)));
            return;
        }
        if (!Objects.equals(beforeRef.ruleId(), afterRef.ruleId())) {
            differences.add(new NodeDifference(screenId, before.logicalNodeId(), DifferenceType.RULE_ID_CHANGED,
                    beforeRef.ruleId(), afterRef.ruleId()));
        }
    }

    private String describe(ResolvedComponentRef ref) {
        if (ref == null) {
            return null;
        }
        return "componentSetKey=" + ref.componentSetKey() + ", variantKey=" + ref.variantKey()
                + ", ruleId=" + ref.ruleId();
    }

    public enum DifferenceType { PATTERN_CHANGED, NODE_STRUCTURE_CHANGED, COMPONENT_RESOLUTION_CHANGED, RULE_ID_CHANGED }

    public record NodeDifference(
            String screenId, String logicalNodeId, DifferenceType type, String before, String after) {}

    public record ShadowComparisonResult(String screenId, List<NodeDifference> differences) {
        public boolean identical() {
            return differences.isEmpty();
        }
    }
}
