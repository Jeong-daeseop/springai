package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Scope Graph의 Dependency Closure 누락·순환을 Apply 전에 검증한다. */
@Service
public class DependencyClosureValidator {

    public ValidationResult validate(DesignDependencyGraph graph, List<String> rootNodeIds) {
        if (graph == null) throw new IllegalArgumentException("DesignDependencyGraph는 필수입니다.");
        List<String> roots = rootNodeIds == null ? List.of() : rootNodeIds.stream().distinct().toList();
        Set<String> nodeIds = graph.nodes().stream().map(DesignDependencyGraph.Node::id).collect(java.util.stream.Collectors.toSet());
        List<String> missingRoots = roots.stream().filter(root -> !nodeIds.contains(root)).sorted().toList();
        Map<String, List<String>> adjacency = new HashMap<>();
        graph.edges().forEach(edge -> adjacency.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge.to()));
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>(roots.stream().filter(nodeIds::contains).toList());
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!reachable.add(current)) continue;
            queue.addAll(adjacency.getOrDefault(current, List.of()));
        }
        List<String> missingClosure = nodeIds.stream().filter(node -> !reachable.contains(node)).sorted().toList();
        List<List<String>> cycles = graph.cycles();
        return new ValidationResult(roots, missingRoots, missingClosure, cycles);
    }

    public ValidationResult requireValid(DesignDependencyGraph graph, List<String> rootNodeIds) {
        ValidationResult result = validate(graph, rootNodeIds);
        if (!result.valid()) throw new DependencyClosureException(result);
        return result;
    }

    public record ValidationResult(
            List<String> rootNodeIds,
            List<String> missingRoots,
            List<String> missingClosureNodes,
            List<List<String>> cycles
    ) {
        public ValidationResult {
            rootNodeIds = List.copyOf(rootNodeIds == null ? List.of() : rootNodeIds);
            missingRoots = List.copyOf(missingRoots == null ? List.of() : missingRoots);
            missingClosureNodes = List.copyOf(missingClosureNodes == null ? List.of() : missingClosureNodes);
            cycles = cycles == null ? List.of() : cycles.stream().map(List::copyOf).toList();
        }

        public boolean valid() {
            return missingRoots.isEmpty() && missingClosureNodes.isEmpty() && cycles.isEmpty();
        }

        public List<String> issues() {
            List<String> issues = new ArrayList<>();
            missingRoots.forEach(root -> issues.add("DEPENDENCY_ROOT_MISSING: " + root));
            missingClosureNodes.forEach(node -> issues.add("DEPENDENCY_CLOSURE_MISSING: " + node));
            cycles.forEach(cycle -> issues.add("DEPENDENCY_CYCLE: " + String.join(" -> ", cycle)));
            return List.copyOf(issues);
        }
    }

    public static final class DependencyClosureException extends IllegalStateException {
        private final ValidationResult result;

        public DependencyClosureException(ValidationResult result) {
            super("Dependency Closure 검증 실패: " + result.issues());
            this.result = result;
        }

        public ValidationResult result() {
            return result;
        }
    }
}
