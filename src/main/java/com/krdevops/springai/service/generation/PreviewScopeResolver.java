package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.DesignDependencyGraph;
import com.krdevops.springai.model.generation.PreviewScope;
import org.springframework.stereotype.Service;

import java.util.List;

/** Preview Scope를 Graph Node로 해석하고 대상 범위를 검증한다. */
@Service
public class PreviewScopeResolver {

    public ResolvedScope resolve(DesignDependencyGraph graph, PreviewScope scope) {
        if (graph == null) throw new IllegalArgumentException("DesignDependencyGraph는 필수입니다.");
        if (scope == null) throw new IllegalArgumentException("PreviewScope는 필수입니다.");
        String nodeId = switch (scope.type()) {
            case SCREEN -> normalize(scope.targetId(), "screen:");
            case SECTION -> normalize(scope.targetId(), "section:");
            case FRAGMENT -> normalize(scope.targetId(), "fragment:");
        };
        DesignDependencyGraph.Node target = graph.nodes().stream()
                .filter(node -> node.id().equals(nodeId)).findFirst().orElse(null);
        if (target == null) throw new PreviewScopeException("PREVIEW_SCOPE_TARGET_MISSING", nodeId);
        boolean typeMatches = switch (scope.type()) {
            case SCREEN -> target.type() == DesignDependencyGraph.NodeType.SCREEN;
            case SECTION -> target.type() == DesignDependencyGraph.NodeType.FRAGMENT;
            case FRAGMENT -> target.type() == DesignDependencyGraph.NodeType.FRAGMENT;
        };
        if (!typeMatches) throw new PreviewScopeException("PREVIEW_SCOPE_TYPE_MISMATCH", nodeId);
        return new ResolvedScope(scope, nodeId, reachable(graph, nodeId));
    }

    private static List<String> reachable(DesignDependencyGraph graph, String root) {
        java.util.Set<String> seen = new java.util.TreeSet<>();
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!seen.add(current)) continue;
            graph.edges().stream().filter(edge -> edge.from().equals(current)).forEach(edge -> queue.add(edge.to()));
        }
        return List.copyOf(seen);
    }

    private static String normalize(String value, String prefix) {
        return value.startsWith(prefix) ? value : prefix + value;
    }

    public record ResolvedScope(PreviewScope requested, String rootNodeId, List<String> includedNodeIds) {
        public ResolvedScope {
            includedNodeIds = List.copyOf(includedNodeIds == null ? List.of() : includedNodeIds);
        }
    }

    public static final class PreviewScopeException extends IllegalArgumentException {
        private final String code;

        public PreviewScopeException(String code, String target) {
            super(code + ": Preview Scope 대상을 해석할 수 없습니다: " + target);
            this.code = code;
        }

        public String code() { return code; }
    }
}
