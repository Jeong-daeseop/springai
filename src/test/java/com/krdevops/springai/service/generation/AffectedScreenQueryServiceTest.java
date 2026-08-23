package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AffectedScreenQueryServiceTest {
    @Test
    void 공유된_token에서_영향받는_두_screen을_역방향_조회한다() {
        var graph = new DesignDependencyGraph(
                List.of(screen("screen:b"), screen("screen:a"), fragment("fragment:f"),
                        new DesignDependencyGraph.Node("token:primary", DesignDependencyGraph.NodeType.TOKEN, "primary")),
                List.of(edge("screen:a", "fragment:f"), edge("screen:b", "fragment:f"),
                        new DesignDependencyGraph.Edge("fragment:f", "token:primary",
                                DesignDependencyGraph.EdgeType.USES_TOKEN)));

        var result = new AffectedScreenQueryService().find(graph, "token:primary");

        assertThat(result.dependencyExists()).isTrue();
        assertThat(result.screenNodeIds()).containsExactly("screen:a", "screen:b");
        assertThat(result.affectsAnyScreen()).isTrue();
    }

    @Test
    void 없는_dependency는_존재하지_않음으로_반환한다() {
        var result = new AffectedScreenQueryService().find(
                new DesignDependencyGraph(List.of(screen("screen:a")), List.of()), "token:missing");

        assertThat(result.dependencyExists()).isFalse();
        assertThat(result.screenNodeIds()).isEmpty();
    }

    private static DesignDependencyGraph.Node screen(String id) {
        return new DesignDependencyGraph.Node(id, DesignDependencyGraph.NodeType.SCREEN, id);
    }

    private static DesignDependencyGraph.Node fragment(String id) {
        return new DesignDependencyGraph.Node(id, DesignDependencyGraph.NodeType.FRAGMENT, id);
    }

    private static DesignDependencyGraph.Edge edge(String from, String to) {
        return new DesignDependencyGraph.Edge(from, to, DesignDependencyGraph.EdgeType.USES_FRAGMENT);
    }
}
