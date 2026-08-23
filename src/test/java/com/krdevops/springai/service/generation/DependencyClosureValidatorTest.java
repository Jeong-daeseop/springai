package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DependencyClosureValidatorTest {
    @Test
    void root에서_도달하지_못하는_고립노드를_누락으로_판정한다() {
        var graph = graph(List.of(
                node("screen:s"), node("fragment:f"), node("token:t"), node("asset:orphan")),
                List.of(edge("screen:s", "fragment:f"), edge("fragment:f", "token:t")));

        var result = new DependencyClosureValidator().validate(graph, List.of("screen:s"));

        assertThat(result.valid()).isFalse();
        assertThat(result.missingClosureNodes()).containsExactly("asset:orphan");
    }

    @Test
    void 순환과_존재하지_않는_root를_차단한다() {
        var graph = graph(List.of(node("screen:s"), node("fragment:f")),
                List.of(edge("screen:s", "fragment:f"), edge("fragment:f", "screen:s")));

        var validator = new DependencyClosureValidator();
        var result = validator.validate(graph, List.of("screen:missing"));

        assertThat(result.missingRoots()).containsExactly("screen:missing");
        assertThat(result.cycles()).isNotEmpty();
        assertThatThrownBy(() -> validator.requireValid(graph, List.of("screen:missing")))
                .isInstanceOf(DependencyClosureValidator.DependencyClosureException.class);
    }

    private static DesignDependencyGraph.Node node(String id) {
        return new DesignDependencyGraph.Node(id, DesignDependencyGraph.NodeType.FRAGMENT, id);
    }

    private static DesignDependencyGraph.Edge edge(String from, String to) {
        return new DesignDependencyGraph.Edge(from, to, DesignDependencyGraph.EdgeType.NESTS_FRAGMENT);
    }

    private static DesignDependencyGraph graph(List<DesignDependencyGraph.Node> nodes,
                                               List<DesignDependencyGraph.Edge> edges) {
        return new DesignDependencyGraph(nodes, edges);
    }
}
