package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaUiDesignSpecV2MapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FigmaUiDesignSpecV2Mapper mapper = new FigmaUiDesignSpecV2Mapper();

    @Test
    void Figma_Node_ID와_Geometry_Interaction을_근거로_보존한다() throws Exception {
        FigmaNodeDocument document = document("""
                {
                  "id":"1:1","type":"FRAME","name":"QNA List","layoutMode":"VERTICAL",
                  "fills":[{"type":"SOLID","opacity":0.5,"color":{"r":1,"g":0,"b":0,"a":0.8}}],
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:2","type":"TEXT","name":"Title","characters":"문의 목록",
                     "absoluteBoundingBox":{"x":40,"y":40,"width":200,"height":32}},
                    {"id":"1:3","type":"INSTANCE","name":"Button","layoutMode":"HORIZONTAL",
                     "absoluteBoundingBox":{"x":1200,"y":40,"width":120,"height":48},
                     "interactions":[{"trigger":{"type":"ON_CLICK"},"actions":[{"type":"NAVIGATE"}]}]},
                    {"id":"1:4","type":"VECTOR","name":"Complex Icon",
                     "absoluteBoundingBox":{"x":10,"y":10,"width":20,"height":20}}
                  ]
                }
                """);

        UiDesignSpecV2 spec = mapper.map(
                "ui-figma-1", new FigmaReference("file-1", "1:1"), document, "crud");

        assertThat(spec.source().sourceRevision()).isEqualTo("figma-r1");
        assertThat(spec.responsiveStructureSet()).extracting(UiDesignSpecV2.ResponsiveStructure::viewportId)
                .containsExactly("desktop");
        assertThat(spec.nodes()).filteredOn(node -> node.semanticId().equals("node-1:3"))
                .singleElement().satisfies(node -> {
                    assertThat(node.evidence().sourceNodeRefs()).containsExactly("1:3");
                    assertThat(node.geometry().width()).isEqualTo(120);
                    assertThat(node.interactionCandidates()).hasSize(1);
                });
        assertThat(spec.nodes()).filteredOn(node -> node.semanticId().equals("node-1:1"))
                .singleElement().satisfies(node -> {
                    assertThat(node.visualStyle()).isNotNull();
                    assertThat(node.visualStyle().fills()).singleElement().satisfies(paint -> {
                        assertThat(paint.type()).isEqualTo("SOLID");
                        assertThat(paint.opacity()).isEqualTo(0.5);
                        assertThat(paint.color()).isEqualTo("rgba(255,0,0,0.80)");
                    });
                });
        assertThat(spec.renderabilityAssessments())
                .filteredOn(value -> value.semanticId().equals("node-1:4"))
                .singleElement().satisfies(value -> {
                    assertThat(value.decision()).isEqualTo(UiDesignSpecV2.RenderabilityDecision.APPROXIMATED);
                    assertThat(value.approved()).isFalse();
                });
    }

    @Test
    void 같은_Figma_Revision은_동일한_Hash와_Node_순서를_생성한다() throws Exception {
        FigmaNodeDocument document = document("""
                {"id":"1:1","type":"FRAME","name":"List",
                 "absoluteBoundingBox":{"x":0,"y":0,"width":800,"height":600},"children":[]}
                """);
        FigmaReference reference = new FigmaReference("file-1", "1:1");

        UiDesignSpecV2 first = mapper.map("ui-1", reference, document, "crud");
        UiDesignSpecV2 second = mapper.map("ui-1", reference, document, "crud");

        assertThat(first.contentHash()).isEqualTo(second.contentHash());
        assertThat(first.nodes()).isEqualTo(second.nodes());
    }

    @Test
    void visualStyle은_JSON_roundTrip에서_보존된다() throws Exception {
        FigmaNodeDocument document = document("""
                {"id":"1:1","type":"FRAME","name":"List",
                 "opacity":0.8,"absoluteBoundingBox":{"x":0,"y":0,"width":800,"height":600},
                 "fills":[{"type":"IMAGE","imageRef":"asset-1","scaleMode":"FILL"}],"children":[]}
                """);
        UiDesignSpecV2 original = mapper.map("ui-visual", new FigmaReference("file-1", "1:1"), document, "crud");

        UiDesignSpecV2 restored = objectMapper.readValue(
                objectMapper.writeValueAsString(original), UiDesignSpecV2.class);

        assertThat(restored.nodes()).singleElement().satisfies(node -> {
            assertThat(node.visualStyle().opacity()).isEqualTo(0.8);
            assertThat(node.visualStyle().fills()).singleElement().satisfies(paint -> {
                assertThat(paint.type()).isEqualTo("IMAGE");
                assertThat(paint.imageRef()).isEqualTo("asset-1");
                assertThat(paint.scaleMode()).isEqualTo("FILL");
            });
        });
    }

    @Test
    void 단일_FRAME이_아니면_거부한다() throws Exception {
        FigmaNodeDocument document = document("""
                {"id":"0:1","type":"SECTION","name":"Screens","children":[]}
                """);

        assertThatThrownBy(() -> mapper.map(
                "ui-1", new FigmaReference("file-1", "0:1"), document, "crud"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FRAME");
    }

    private FigmaNodeDocument document(String json) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        return new FigmaNodeDocument("figma-r1", node);
    }
}
