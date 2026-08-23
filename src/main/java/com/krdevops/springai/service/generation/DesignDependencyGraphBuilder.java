package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** UiDesignSpec v2와 승인 Component Mapping에서 디자인 의존성 Graph를 만든다. */
@Service
public class DesignDependencyGraphBuilder {

    public DesignDependencyGraph build(UiDesignSpecV2 spec, List<DesignComponentRenderInput> components) {
        if (spec == null) throw new IllegalArgumentException("UiDesignSpecV2는 필수입니다.");
        Map<String, DesignDependencyGraph.Node> nodes = new LinkedHashMap<>();
        Set<DesignDependencyGraph.Edge> edges = new LinkedHashSet<>();
        String screenId = "screen:" + spec.specId();
        add(nodes, new DesignDependencyGraph.Node(screenId, DesignDependencyGraph.NodeType.SCREEN, spec.specId()));

        for (UiDesignSpecV2.SemanticNode semanticNode : spec.nodes()) {
            if (semanticNode.componentRef() != null) {
                String fragment = "fragment:" + semanticNode.componentRef().logicalType();
                add(nodes, new DesignDependencyGraph.Node(fragment, DesignDependencyGraph.NodeType.FRAGMENT,
                        semanticNode.componentRef().logicalType()));
                edges.add(new DesignDependencyGraph.Edge(screenId, fragment,
                        DesignDependencyGraph.EdgeType.USES_FRAGMENT));
            }
            for (UiDesignSpecV2.TokenBinding tokenBinding : semanticNode.tokenBindings()) {
                String token = "token:" + tokenBinding.tokenName();
                add(nodes, new DesignDependencyGraph.Node(token, DesignDependencyGraph.NodeType.TOKEN,
                        tokenBinding.tokenName()));
                String owner = semanticNode.componentRef() == null ? screenId
                        : "fragment:" + semanticNode.componentRef().logicalType();
                edges.add(new DesignDependencyGraph.Edge(owner, token,
                        DesignDependencyGraph.EdgeType.USES_TOKEN));
            }
        }

        for (DesignComponentRenderInput component : components == null ? List.<DesignComponentRenderInput>of() : components) {
            String fragment = "fragment:" + component.thymeleafFragment();
            add(nodes, new DesignDependencyGraph.Node(fragment, DesignDependencyGraph.NodeType.FRAGMENT,
                    component.thymeleafFragment()));
            edges.add(new DesignDependencyGraph.Edge(screenId, fragment,
                    DesignDependencyGraph.EdgeType.USES_FRAGMENT));
            addValueDependencies(component.fragmentParameters(), fragment, nodes, edges);
            addValueDependencies(component.fragmentRegions(), fragment, nodes, edges);
        }
        return new DesignDependencyGraph(new ArrayList<>(nodes.values()), new ArrayList<>(edges));
    }

    private static void addValueDependencies(Map<String, Object> values, String owner,
                                             Map<String, DesignDependencyGraph.Node> nodes,
                                             Set<DesignDependencyGraph.Edge> edges) {
        if (values == null) return;
        values.forEach((key, value) -> {
            if (value instanceof String text) {
                if (text.startsWith("token:")) {
                    String name = text.substring("token:".length());
                    add(nodes, new DesignDependencyGraph.Node(text, DesignDependencyGraph.NodeType.TOKEN, name));
                    edges.add(new DesignDependencyGraph.Edge(owner, text, DesignDependencyGraph.EdgeType.USES_TOKEN));
                } else if (text.startsWith("asset:")) {
                    String name = text.substring("asset:".length());
                    add(nodes, new DesignDependencyGraph.Node(text, DesignDependencyGraph.NodeType.ASSET, name));
                    edges.add(new DesignDependencyGraph.Edge(owner, text, DesignDependencyGraph.EdgeType.USES_ASSET));
                } else if (text.startsWith("fragment:")) {
                    add(nodes, new DesignDependencyGraph.Node(text, DesignDependencyGraph.NodeType.FRAGMENT, text.substring(9)));
                    edges.add(new DesignDependencyGraph.Edge(owner, text, DesignDependencyGraph.EdgeType.NESTS_FRAGMENT));
                }
            }
        });
    }

    private static void add(Map<String, DesignDependencyGraph.Node> nodes, DesignDependencyGraph.Node node) {
        DesignDependencyGraph.Node previous = nodes.putIfAbsent(node.id(), node);
        if (previous != null && previous.type() != node.type()) {
            throw new IllegalArgumentException("Graph Node ID가 서로 다른 유형으로 사용되었습니다: " + node.id());
        }
    }
}
