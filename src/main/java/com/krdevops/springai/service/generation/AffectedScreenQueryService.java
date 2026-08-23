package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Dependency 변경 대상에서 역방향으로 영향받는 Screen을 조회한다. */
@Service
public class AffectedScreenQueryService {

    public AffectedScreens find(DesignDependencyGraph graph, String dependencyNodeId) {
        if (graph == null) throw new IllegalArgumentException("DesignDependencyGraph는 필수입니다.");
        if (dependencyNodeId == null || dependencyNodeId.isBlank()) {
            throw new IllegalArgumentException("dependencyNodeId는 필수입니다.");
        }
        Set<String> nodeIds = graph.nodes().stream().map(DesignDependencyGraph.Node::id)
                .collect(java.util.stream.Collectors.toSet());
        if (!nodeIds.contains(dependencyNodeId)) {
            return new AffectedScreens(dependencyNodeId, false, List.of());
        }
        Map<String, List<String>> reverse = new HashMap<>();
        graph.edges().forEach(edge -> reverse.computeIfAbsent(edge.to(), ignored -> new java.util.ArrayList<>())
                .add(edge.from()));
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(dependencyNodeId);
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!visited.add(current)) continue;
            queue.addAll(reverse.getOrDefault(current, List.of()));
        }
        List<String> screens = graph.nodes().stream()
                .filter(node -> node.type() == DesignDependencyGraph.NodeType.SCREEN)
                .map(DesignDependencyGraph.Node::id)
                .filter(visited::contains)
                .sorted()
                .toList();
        return new AffectedScreens(dependencyNodeId, true, screens);
    }

    public record AffectedScreens(
            String dependencyNodeId,
            boolean dependencyExists,
            List<String> screenNodeIds
    ) {
        public AffectedScreens {
            screenNodeIds = List.copyOf(screenNodeIds == null ? List.of() : screenNodeIds);
        }

        public boolean affectsAnyScreen() {
            return !screenNodeIds.isEmpty();
        }
    }
}
