package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UiDesignSpec v2의 Viewport 의미 변화와 Version 간 Design IR 변경을 결정적으로 계산한다. */
@Service
public class UiDesignSpecV2DiffService {

    public List<ResponsiveChange> analyzeResponsive(UiDesignSpecV2 spec) {
        if (spec == null) throw new IllegalArgumentException("UiDesignSpecV2는 필수입니다.");
        List<UiDesignSpecV2.ResponsiveStructure> structures = spec.responsiveStructureSet().stream()
                .sorted(Comparator.comparingInt(
                                (UiDesignSpecV2.ResponsiveStructure value) -> viewportRank(value.viewportId()))
                        .reversed()
                        .thenComparing(UiDesignSpecV2.ResponsiveStructure::viewportId))
                .toList();
        if (structures.size() < 2) return List.of();
        UiDesignSpecV2.ResponsiveStructure base = structures.get(0);
        List<ResponsiveChange> changes = new ArrayList<>();
        for (int index = 1; index < structures.size(); index++) {
            compareViewports(base, structures.get(index), changes);
        }
        return List.copyOf(changes);
    }

    public SpecDiff compare(UiDesignSpecV2 base, UiDesignSpecV2 target) {
        if (base == null || target == null) {
            throw new IllegalArgumentException("비교할 base와 target UiDesignSpecV2가 필요합니다.");
        }
        Map<String, UiDesignSpecV2.SemanticNode> baseNodes = byId(base.nodes());
        Map<String, UiDesignSpecV2.SemanticNode> targetNodes = byId(target.nodes());
        LinkedHashSet<String> ids = new LinkedHashSet<>(baseNodes.keySet());
        ids.addAll(targetNodes.keySet());
        List<NodeChange> nodeChanges = new ArrayList<>();
        for (String id : ids) {
            UiDesignSpecV2.SemanticNode before = baseNodes.get(id);
            UiDesignSpecV2.SemanticNode after = targetNodes.get(id);
            if (before == null) nodeChanges.add(new NodeChange(id, ChangeType.ADDED, List.of()));
            else if (after == null) nodeChanges.add(new NodeChange(id, ChangeType.REMOVED, List.of()));
            else {
                List<String> fields = changedFields(before, after);
                if (!fields.isEmpty()) nodeChanges.add(new NodeChange(id, ChangeType.MODIFIED, fields));
            }
        }
        List<ViewportVersionChange> viewportChanges = compareStructures(
                base.responsiveStructureSet(), target.responsiveStructureSet());
        boolean sourceRevisionChanged = !base.source().sourceRevision()
                .equals(target.source().sourceRevision());
        boolean contentChanged = !base.contentHash().equals(target.contentHash());
        return new SpecDiff(
                base.specId(), target.specId(), base.contentHash(), target.contentHash(),
                contentChanged, sourceRevisionChanged, List.copyOf(nodeChanges),
                viewportChanges, analyzeResponsive(target));
    }

    private void compareViewports(
            UiDesignSpecV2.ResponsiveStructure base,
            UiDesignSpecV2.ResponsiveStructure target,
            List<ResponsiveChange> changes) {
        LinkedHashSet<String> hidden = difference(base.visibleSemanticIds(), target.visibleSemanticIds());
        LinkedHashSet<String> added = difference(target.visibleSemanticIds(), base.visibleSemanticIds());
        int symmetricDifference = hidden.size() + added.size();
        if (symmetricDifference > Math.max(2, base.visibleSemanticIds().size())) {
            changes.add(new ResponsiveChange(
                    base.viewportId(), target.viewportId(), ResponsiveChangeType.ALTERNATE_STRUCTURE,
                    List.copyOf(hidden), List.copyOf(added), "Viewport의 주요 구조가 교체되었습니다."));
            return;
        }
        if (!hidden.isEmpty() && hidden.size() == added.size()) {
            changes.add(new ResponsiveChange(
                    base.viewportId(), target.viewportId(), ResponsiveChangeType.SWAP,
                    List.copyOf(hidden), List.copyOf(added), "동일 수의 Node가 다른 Node로 교체되었습니다."));
            hidden.clear();
            added.clear();
        }
        if (!hidden.isEmpty()) {
            changes.add(new ResponsiveChange(
                    base.viewportId(), target.viewportId(), ResponsiveChangeType.HIDE,
                    List.copyOf(hidden), List.of(), "좁은 Viewport에서 Node가 숨겨졌습니다."));
        }
        if (commonOrderChanged(base.order(), target.order())) {
            changes.add(new ResponsiveChange(
                    base.viewportId(), target.viewportId(), ResponsiveChangeType.REFLOW,
                    common(base.order(), target.order()), List.of(), "공통 Node의 표시 순서가 바뀌었습니다."));
        }
    }

    private List<ViewportVersionChange> compareStructures(
            List<UiDesignSpecV2.ResponsiveStructure> base,
            List<UiDesignSpecV2.ResponsiveStructure> target) {
        Map<String, UiDesignSpecV2.ResponsiveStructure> before = base.stream()
                .collect(Collectors.toMap(UiDesignSpecV2.ResponsiveStructure::viewportId, Function.identity()));
        Map<String, UiDesignSpecV2.ResponsiveStructure> after = target.stream()
                .collect(Collectors.toMap(UiDesignSpecV2.ResponsiveStructure::viewportId, Function.identity()));
        LinkedHashSet<String> viewports = new LinkedHashSet<>(before.keySet());
        viewports.addAll(after.keySet());
        List<ViewportVersionChange> changes = new ArrayList<>();
        for (String viewport : viewports) {
            UiDesignSpecV2.ResponsiveStructure left = before.get(viewport);
            UiDesignSpecV2.ResponsiveStructure right = after.get(viewport);
            if (left == null) changes.add(new ViewportVersionChange(viewport, ChangeType.ADDED));
            else if (right == null) changes.add(new ViewportVersionChange(viewport, ChangeType.REMOVED));
            else if (!left.equals(right)) changes.add(new ViewportVersionChange(viewport, ChangeType.MODIFIED));
        }
        return List.copyOf(changes);
    }

    private List<String> changedFields(
            UiDesignSpecV2.SemanticNode base,
            UiDesignSpecV2.SemanticNode target) {
        List<String> fields = new ArrayList<>();
        if (!base.role().equals(target.role())) fields.add("role");
        if (!java.util.Objects.equals(base.logicalType(), target.logicalType())) fields.add("logicalType");
        if (!java.util.Objects.equals(base.geometry(), target.geometry())) fields.add("geometry");
        if (!base.layoutConstraints().equals(target.layoutConstraints())) fields.add("layoutConstraints");
        if (!java.util.Objects.equals(base.componentRef(), target.componentRef())) fields.add("componentRef");
        if (!base.tokenBindings().equals(target.tokenBindings())) fields.add("tokenBindings");
        if (!base.interactionCandidates().equals(target.interactionCandidates())) fields.add("interactionCandidates");
        if (!base.evidence().equals(target.evidence())) fields.add("evidence");
        return List.copyOf(fields);
    }

    private Map<String, UiDesignSpecV2.SemanticNode> byId(List<UiDesignSpecV2.SemanticNode> nodes) {
        Map<String, UiDesignSpecV2.SemanticNode> result = new LinkedHashMap<>();
        nodes.forEach(node -> result.put(node.semanticId(), node));
        return result;
    }

    private LinkedHashSet<String> difference(List<String> left, List<String> right) {
        LinkedHashSet<String> result = new LinkedHashSet<>(left);
        result.removeAll(new LinkedHashSet<>(right));
        return result;
    }

    private boolean commonOrderChanged(List<String> base, List<String> target) {
        return !common(base, target).equals(common(target, base));
    }

    private List<String> common(List<String> source, List<String> other) {
        Set<String> allowed = new LinkedHashSet<>(other);
        return source.stream().filter(allowed::contains).toList();
    }

    private int viewportRank(String viewportId) {
        String normalized = viewportId.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("desktop")) return 3;
        if (normalized.contains("tablet")) return 2;
        if (normalized.contains("mobile")) return 1;
        return 0;
    }

    public enum ResponsiveChangeType { REFLOW, HIDE, SWAP, ALTERNATE_STRUCTURE }
    public enum ChangeType { ADDED, REMOVED, MODIFIED }

    public record ResponsiveChange(
            String baseViewport,
            String targetViewport,
            ResponsiveChangeType type,
            List<String> sourceNodeIds,
            List<String> targetNodeIds,
            String description
    ) {}

    public record NodeChange(String semanticId, ChangeType type, List<String> changedFields) {}
    public record ViewportVersionChange(String viewportId, ChangeType type) {}

    public record SpecDiff(
            String baseSpecId,
            String targetSpecId,
            String baseContentHash,
            String targetContentHash,
            boolean contentChanged,
            boolean sourceRevisionChanged,
            List<NodeChange> nodeChanges,
            List<ViewportVersionChange> viewportChanges,
            List<ResponsiveChange> targetResponsiveChanges
    ) {
        public SpecDiff {
            nodeChanges = List.copyOf(nodeChanges);
            viewportChanges = List.copyOf(viewportChanges);
            targetResponsiveChanges = List.copyOf(targetResponsiveChanges);
        }
    }
}
