package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.DesignDependencyGraph;
import com.krdevops.springai.model.generation.PreviewScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewScopeResolverTest {
    @Test
    void fragment_scope는_하위_의존성만_포함한다() {
        var graph = new DesignDependencyGraph(
                List.of(node("fragment:button", DesignDependencyGraph.NodeType.FRAGMENT),
                        node("token:primary", DesignDependencyGraph.NodeType.TOKEN),
                        node("asset:icon", DesignDependencyGraph.NodeType.ASSET)),
                List.of(new DesignDependencyGraph.Edge("fragment:button", "token:primary", DesignDependencyGraph.EdgeType.USES_TOKEN),
                        new DesignDependencyGraph.Edge("fragment:button", "asset:icon", DesignDependencyGraph.EdgeType.USES_ASSET)));

        var result = new PreviewScopeResolver().resolve(graph, PreviewScope.fragment("button"));

        assertThat(result.rootNodeId()).isEqualTo("fragment:button");
        assertThat(result.includedNodeIds()).containsExactly("asset:icon", "fragment:button", "token:primary");
    }

    @Test
    void 없는_scope_대상은_차단한다() {
        var graph = new DesignDependencyGraph(
                List.of(node("screen:s1", DesignDependencyGraph.NodeType.SCREEN)), List.of());
        assertThatThrownBy(() -> new PreviewScopeResolver().resolve(graph, PreviewScope.section("missing")))
                .isInstanceOf(PreviewScopeResolver.PreviewScopeException.class)
                .hasMessageContaining("PREVIEW_SCOPE_TARGET_MISSING");
    }

    private static DesignDependencyGraph.Node node(String id, DesignDependencyGraph.NodeType type) {
        return new DesignDependencyGraph.Node(id, type, id);
    }
}
