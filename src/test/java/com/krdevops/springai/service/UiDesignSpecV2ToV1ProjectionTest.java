package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UiDesignSpecV2ToV1ProjectionTest {

    private final UiDesignSpecV2ToV1Projection projection = new UiDesignSpecV2ToV1Projection();

    @Test
    void Field_후보만_투영하고_시각_Action은_업무_Command로_확정하지_않는다() {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:1"), 0.9, "TEST", false, false);
        UiDesignSpecV2 spec = spec(List.of(
                new UiDesignSpecV2.SemanticNode("field-title", "field-candidate", null, evidence, List.of()),
                new UiDesignSpecV2.SemanticNode("action-search", "action-candidate", null, evidence, List.of()),
                new UiDesignSpecV2.SemanticNode("node-label", "text", null, evidence, List.of())));

        UiDesignSpec result = projection.project(spec, "crud");

        assertThat(result.fieldHints()).extracting(UiDesignSpec.FieldHint::id)
                .containsExactly("title");
        assertThat(result.actions()).isEmpty();
    }

    @Test
    void VisualStyle을_v1_geometry로_명시적으로_투영한다() {
        UiDesignSpecV2.InferenceEvidence evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:2"), 0.9, "TEST", false, false);
        UiDesignSpecV2.VisualStyle style = new UiDesignSpecV2.VisualStyle(0.75,
                List.of(new UiDesignSpecV2.VisualPaint("SOLID", true, 0.5,
                        "rgba(255,0,0,0.80)", null, null)),
                List.of(new UiDesignSpecV2.VisualPaint("IMAGE", true, 1,
                        null, "asset-1", "FILL")));
        UiDesignSpecV2.SemanticNode node = new UiDesignSpecV2.SemanticNode(
                "node-1:2", "container", "CARD",
                new UiDesignSpecV2.Geometry(1, 2, 100, 40), java.util.Map.of(), null, style,
                List.of(), List.of(), evidence);

        UiDesignSpec result = projection.project(spec(List.of(node)), "crud");

        assertThat(result.geometryTree()).singleElement().satisfies(geometry -> {
            assertThat(geometry.opacity()).isEqualTo(0.75);
            assertThat(geometry.fills()).singleElement().satisfies(paint ->
                    assertThat(paint.color()).isEqualTo("rgba(255,0,0,0.80)"));
            assertThat(geometry.strokes()).singleElement().satisfies(paint ->
                    assertThat(paint.type()).isEqualTo("IMAGE"));
        });
    }

    private UiDesignSpecV2 spec(List<UiDesignSpecV2.SemanticNode> nodes) {
        List<String> ids = nodes.stream().map(UiDesignSpecV2.SemanticNode::semanticId).toList();
        return new UiDesignSpecV2(
                "ui-1", "2.0", "a".repeat(64),
                new UiDesignSpecV2.Source(UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "r1"),
                null, nodes, List.of(),
                List.of(new UiDesignSpecV2.ResponsiveStructure("desktop", ids, ids)),
                List.of(), List.of(), 0.9);
    }
}
