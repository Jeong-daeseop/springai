package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DesignDependencyGraphBuilderTest {
    @Test
    void Screen에서_Fragment_Token_Asset_의존성을_구성한다() {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("node-1"), 0.9, "figma", false, false);
        UiDesignSpecV2 spec = new UiDesignSpecV2("screen-1", "2.0", "a".repeat(64),
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.IMAGE, null, null, "r1"), null,
                List.of(new UiDesignSpecV2.SemanticNode("n1", "button", "button", evidence,
                        List.of(new UiDesignSpecV2.TokenBinding("color", "color.primary", null)))),
                List.of(), List.of(), List.of(), List.of(), 0.9);
        DesignComponentRenderInput input = new DesignComponentRenderInput("m1", "1.0", "button", "set.button",
                "fragments/button", "thymeleaf-krds", Map.of("icon", "asset:icon.search"),
                Map.of("suffix", "fragment:fragments/icon"), "r1", "b".repeat(64));

        var graph = new DesignDependencyGraphBuilder().build(spec, List.of(input));

        assertThat(graph.nodes()).extracting("type").contains(
                com.krdevops.springai.model.generation.DesignDependencyGraph.NodeType.SCREEN,
                com.krdevops.springai.model.generation.DesignDependencyGraph.NodeType.FRAGMENT,
                com.krdevops.springai.model.generation.DesignDependencyGraph.NodeType.TOKEN,
                com.krdevops.springai.model.generation.DesignDependencyGraph.NodeType.ASSET);
        assertThat(graph.edges()).isNotEmpty();
        assertThat(graph.cycles()).isEmpty();
    }
}
