package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 승인 ScreenSpecification의 업무 Binding을 Controller·VO·Mapper 의존성으로 연결한다. */
@Service
public class BindingDependencyGraphBuilder {

    public DesignDependencyGraph bind(DesignDependencyGraph designGraph, ScreenSpecification specification) {
        if (designGraph == null) throw new IllegalArgumentException("DesignDependencyGraph는 필수입니다.");
        if (specification == null) throw new IllegalArgumentException("ScreenSpecification은 필수입니다.");
        String screenId = "screen:" + specification.id();
        if (designGraph.nodes().stream().noneMatch(node -> node.id().equals(screenId))) {
            throw new IllegalArgumentException("Graph에 Screen Node가 없습니다: " + screenId);
        }
        List<DesignDependencyGraph.Node> nodes = new ArrayList<>(designGraph.nodes());
        Set<DesignDependencyGraph.Edge> edges = new LinkedHashSet<>(designGraph.edges());
        String key = normalize(specification.primaryTable(), specification.id());
        addBinding(nodes, edges, screenId, "controller:" + key, "Controller", DesignDependencyGraph.NodeType.BINDING_ARTIFACT,
                DesignDependencyGraph.EdgeType.BINDS_CONTROLLER);
        addBinding(nodes, edges, screenId, "vo:" + key, "VO", DesignDependencyGraph.NodeType.BINDING_ARTIFACT,
                DesignDependencyGraph.EdgeType.BINDS_VO);
        addBinding(nodes, edges, screenId, "mapper:" + key, "Mapper", DesignDependencyGraph.NodeType.BINDING_ARTIFACT,
                DesignDependencyGraph.EdgeType.BINDS_MAPPER);
        addBinding(nodes, edges, screenId, "mapper-xml:" + key, "Mapper XML", DesignDependencyGraph.NodeType.BINDING_ARTIFACT,
                DesignDependencyGraph.EdgeType.BINDS_MAPPER_XML);
        if (hasJoin(specification)) {
            addBinding(nodes, edges, screenId, "mapper-join:" + key, "Mapper JOIN", DesignDependencyGraph.NodeType.BINDING_ARTIFACT,
                    DesignDependencyGraph.EdgeType.BINDS_MAPPER);
        }
        return new DesignDependencyGraph(nodes, new ArrayList<>(edges));
    }

    private static boolean hasJoin(ScreenSpecification specification) {
        return specification.dataSources().stream().anyMatch(source ->
                source != null && !source.primary()
                        && source.table() != null && !source.table().isBlank());
    }

    private static void addBinding(List<DesignDependencyGraph.Node> nodes,
                                   Set<DesignDependencyGraph.Edge> edges,
                                   String screenId, String id, String name,
                                   DesignDependencyGraph.NodeType nodeType,
                                   DesignDependencyGraph.EdgeType edgeType) {
        nodes.stream().filter(node -> node.id().equals(id)).findFirst().ifPresentOrElse(existing -> {
            if (existing.type() != nodeType) throw new IllegalArgumentException("Binding Node 유형이 충돌합니다: " + id);
        }, () -> nodes.add(new DesignDependencyGraph.Node(id, nodeType, name)));
        edges.add(new DesignDependencyGraph.Edge(screenId, id, edgeType));
    }

    private static String normalize(String table, String fallback) {
        String value = table == null || table.isBlank() ? fallback : table;
        return value.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
    }
}
