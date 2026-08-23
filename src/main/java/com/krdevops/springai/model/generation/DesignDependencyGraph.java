package com.krdevops.springai.model.generation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Screen→Fragment→Token·Asset 생성 의존성 그래프. */
public record DesignDependencyGraph(
        List<Node> nodes,
        List<Edge> edges
) {
    public DesignDependencyGraph {
        nodes = nodes == null ? List.of() : nodes.stream().sorted(Comparator.comparing(Node::id)).toList();
        edges = edges == null ? List.of() : edges.stream().sorted(Comparator
                .comparing(Edge::from).thenComparing(Edge::to)).toList();
        Set<String> ids = new HashSet<>();
        nodes.forEach(node -> {
            if (node == null || !ids.add(node.id())) throw new IllegalArgumentException("Graph Node ID가 중복되거나 null입니다.");
        });
        edges.forEach(edge -> {
            if (edge == null || !ids.contains(edge.from()) || !ids.contains(edge.to())) {
                throw new IllegalArgumentException("Graph Edge가 존재하지 않는 Node를 참조합니다.");
            }
        });
    }

    public List<List<String>> cycles() {
        Map<String, List<String>> adjacency = new HashMap<>();
        edges.forEach(edge -> adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to()));
        List<List<String>> result = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();
        for (Node node : nodes) findCycles(node.id(), adjacency, visiting, visited, result);
        return result.stream().map(List::copyOf).toList();
    }

    private static void findCycles(String current, Map<String, List<String>> adjacency,
                                   Set<String> visiting, Set<String> visited, List<List<String>> result) {
        if (visiting.contains(current)) {
            List<String> cycle = new ArrayList<>(visiting);
            int start = cycle.indexOf(current);
            result.add(new ArrayList<>(cycle.subList(start, cycle.size())));
            return;
        }
        if (!visited.add(current)) return;
        visiting.add(current);
        for (String next : adjacency.getOrDefault(current, List.of())) findCycles(next, adjacency, visiting, visited, result);
        visiting.remove(current);
    }

    public record Node(String id, NodeType type, String name) {
        public Node {
            if (id == null || id.isBlank() || name == null || name.isBlank() || type == null) {
                throw new IllegalArgumentException("Graph Node id·type·name은 필수입니다.");
            }
        }
    }

    public record Edge(String from, String to, EdgeType type) {
        public Edge {
            if (from == null || from.isBlank() || to == null || to.isBlank() || type == null) {
                throw new IllegalArgumentException("Graph Edge from·to·type은 필수입니다.");
            }
        }
    }

    public enum NodeType { SCREEN, FRAGMENT, TOKEN, ASSET, BINDING_ARTIFACT }
    public enum EdgeType {
        USES_FRAGMENT, USES_TOKEN, USES_ASSET, NESTS_FRAGMENT,
        BINDS_CONTROLLER, BINDS_VO, BINDS_MAPPER, BINDS_MAPPER_XML
    }
}
