package com.krdevops.springai.model.design;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UiDesignSpecV2Test {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void 정상_Fixture를_역직렬화하고_동일한_계약으로_직렬화한다() throws Exception {
        String json = Files.readString(Path.of(
                "website-figma-contract/fixtures/valid-ui-design-spec-v2.json"));

        UiDesignSpecV2 spec = mapper.readValue(json, UiDesignSpecV2.class);
        String roundTrip = mapper.writeValueAsString(spec);

        assertThat(spec.schemaVersion()).isEqualTo(UiDesignSpecV2.SCHEMA_VERSION);
        assertThat(spec.source().sourceType()).isEqualTo(UiDesignSpecV2.SourceType.FIGMA);
        assertThat(spec.nodes()).extracting(UiDesignSpecV2.SemanticNode::semanticId)
                .containsExactly("root");
        assertThat(roundTrip).contains("\"sourceNodeRefs\":[\"1:2\"]");
    }

    @Test
    void 원본_Node가_없는_추론은_Legacy로_표시하지_않으면_거부한다() {
        assertThatThrownBy(() -> new UiDesignSpecV2.InferenceEvidence(
                List.of(), 0.8, "VISION", true, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceNodeRefs");

        UiDesignSpecV2.InferenceEvidence legacy = new UiDesignSpecV2.InferenceEvidence(
                List.of(), 0.5, "V1_ADAPTER", true, true);
        assertThat(legacy.legacyUnknown()).isTrue();
    }

    @Test
    void SemanticId_중복과_잘못된_Viewport_Order를_거부한다() {
        UiDesignSpecV2.InferenceEvidence evidence = evidence();
        UiDesignSpecV2.SemanticNode first = node("root", evidence);
        UiDesignSpecV2.SemanticNode duplicate = node("root", evidence);

        assertThatThrownBy(() -> spec(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
        assertThatThrownBy(() -> new UiDesignSpecV2.ResponsiveStructure(
                "desktop", List.of("root"), List.of("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("visibleSemanticIds");
    }

    @Test
    void 손실_Rendering은_설명이_필요하고_UNSUPPORTED는_승인할_수_없다() {
        assertThatThrownBy(() -> new UiDesignSpecV2.RenderabilityAssessment(
                "chart", UiDesignSpecV2.RenderabilityDecision.APPROXIMATED, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lossDescription");
        assertThatThrownBy(() -> new UiDesignSpecV2.RenderabilityAssessment(
                "chart", UiDesignSpecV2.RenderabilityDecision.UNSUPPORTED, "미지원", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승인할 수 없습니다");
    }

    @Test
    void 목록과_Map은_방어적으로_복사된다() {
        UiDesignSpecV2.SemanticNode node = new UiDesignSpecV2.SemanticNode(
                "root", "page", null, null, Map.of("layout", "vertical"), null,
                List.of(), List.of(), evidence());
        UiDesignSpecV2 spec = spec(List.of(node));

        assertThatThrownBy(() -> spec.nodes().add(node))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> node.layoutConstraints().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private UiDesignSpecV2 spec(List<UiDesignSpecV2.SemanticNode> nodes) {
        return new UiDesignSpecV2(
                "ui-1", "2.0", "a".repeat(64),
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.FIGMA, "f1", "1:2", "r1"),
                null, nodes, List.of(),
                List.of(new UiDesignSpecV2.ResponsiveStructure(
                        "desktop", List.of("root"), List.of("root"))),
                List.of(), List.of(), 1.0);
    }

    private UiDesignSpecV2.SemanticNode node(
            String id, UiDesignSpecV2.InferenceEvidence evidence) {
        return new UiDesignSpecV2.SemanticNode(id, "page", null, evidence, List.of());
    }

    private UiDesignSpecV2.InferenceEvidence evidence() {
        return new UiDesignSpecV2.InferenceEvidence(
                List.of("1:2"), 1.0, "FIGMA_TREE", false, false);
    }
}
