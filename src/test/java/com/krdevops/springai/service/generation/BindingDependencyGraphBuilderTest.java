package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.generation.DesignDependencyGraph;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BindingDependencyGraphBuilderTest {
    @Test
    void ScreenSpecification을_Controller_VO_Mapper에_연결한다() {
        var graph = new DesignDependencyGraph(
                List.of(new DesignDependencyGraph.Node("screen:s1", DesignDependencyGraph.NodeType.SCREEN, "s1")),
                List.of());
        ScreenSpecification spec = new ScreenSpecification("s1", 1, ScreenSpecStatus.APPROVED,
                "직원", "crud", "list", "db", "EMP", List.of(
                        DataSourceSpec.primary("db", "EMP"),
                        new DataSourceSpec("dept", "db", "DEPT", "d", false, "LEFT", "t.DEPT_ID=d.ID")),
                List.of(), List.of(), null, null, LocalDateTime.now());

        var result = new BindingDependencyGraphBuilder().bind(graph, spec);

        assertThat(result.nodes()).extracting(DesignDependencyGraph.Node::id)
                .contains("controller:EMP", "vo:EMP", "mapper:EMP", "mapper-xml:EMP", "mapper-join:EMP");
        assertThat(result.edges()).extracting(DesignDependencyGraph.Edge::type)
                .contains(DesignDependencyGraph.EdgeType.BINDS_CONTROLLER,
                        DesignDependencyGraph.EdgeType.BINDS_VO,
                        DesignDependencyGraph.EdgeType.BINDS_MAPPER,
                        DesignDependencyGraph.EdgeType.BINDS_MAPPER_XML);
    }
}
