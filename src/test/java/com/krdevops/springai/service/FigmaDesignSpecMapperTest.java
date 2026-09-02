package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FigmaDesignSpecMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FigmaDesignSpecMapper mapper = new FigmaDesignSpecMapper();

    @Test
    void mapsKnownLayersDeterministically() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"게시판 목록", "clipsContent":false,
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "backgroundColor":{"r":1,"g":1,"b":1,"a":1},
                  "children":[
                    {"type":"FRAME","name":"Search Filter","absoluteBoundingBox":{"x":80,"y":100,"width":1280,"height":80}},
                    {"type":"FRAME","name":"Table List","absoluteBoundingBox":{"x":80,"y":220,"width":1280,"height":500}},
                    {"type":"TEXT","name":"title","characters":"제목","absoluteBoundingBox":{"x":100,"y":250,"width":100,"height":24}},
                    {"type":"TEXT","name":"author","characters":"작성자","absoluteBoundingBox":{"x":300,"y":250,"width":100,"height":24}},
                    {"type":"COMPONENT","name":"등록 Button","absoluteBoundingBox":{"x":1200,"y":760,"width":120,"height":44}},
                    {"type":"INSTANCE","name":"버튼 신청하기","absoluteBoundingBox":{"x":1040,"y":760,"width":120,"height":44}}
                  ]
                }
                """));

        var first = mapper.map(document, "board");
        var second = mapper.map(document, "board");

        assertThat(first).isEqualTo(second);
        assertThat(first.archetype()).isEqualTo("BOARD_LIST");
        assertThat(first.components()).extracting(component -> component.type())
                .contains("SEARCH_PANEL", "TABLE");
        assertThat(first.fieldHints()).extracting(field -> field.role())
                .contains(UiFieldRole.TITLE, UiFieldRole.AUTHOR);
        assertThat(first.actions()).extracting(action -> action.type())
                .contains("CREATE", "APPLY")
                .doesNotContain("BACK");
        assertThat(first.layout().formColumnLayout()).isEqualTo("two-column");
        assertThat(first.layout().actionPlacement()).isEqualTo("bottom-right");
    }

    @Test
    void reportsRotationClippingInvisibleAndUnknownSemanticsAsUncertainty() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"화면", "clipsContent":true, "rotation":5,
                  "absoluteBoundingBox":{"x":0,"y":0,"width":800,"height":600},
                  "absoluteRenderBounds":{"x":-8,"y":-8,"width":816,"height":616},
                  "effects":[{"type":"DROP_SHADOW","visible":true}],
                  "children":[
                    {"type":"COMPONENT","name":"Component 99","absoluteBoundingBox":{"x":10,"y":10,"width":20,"height":20}},
                    {"type":"TEXT","name":"Label","characters":"알 수 없음","visible":false,
                     "absoluteBoundingBox":{"x":20,"y":20,"width":100,"height":20}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        assertThat(result.uncertainties())
                .anyMatch(value -> value.contains("회전"))
                .anyMatch(value -> value.contains("클리핑"))
                .anyMatch(value -> value.contains("비가시"))
                .anyMatch(value -> value.contains("effect"))
                .anyMatch(value -> value.contains("absoluteRenderBounds"))
                .anyMatch(value -> value.contains("의미를 알 수 없는 컴포넌트"));
    }

    @Test
    void rejectsSectionThatCanContainMultipleScreens() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"SECTION", "name":"신청 목록 전체 예시",
                  "children":[
                    {"type":"FRAME","name":"카드형 목록"},
                    {"type":"FRAME","name":"리스트형 목록"}
                  ]
                }
                """));

        assertThatThrownBy(() -> mapper.map(document, "crud"))
                .isInstanceOfSatisfying(FigmaApiException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("FIGMA_FRAME_REQUIRED");
                    assertThat(exception.statusCode()).isEqualTo(422);
                    assertThat(exception.getMessage()).contains("단일 화면 FRAME");
                });
    }

    @Test
    void rejectsUnsupportedRootNodeType() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {"type":"COMPONENT", "name":"검색 입력 컴포넌트"}
                """));

        assertThatThrownBy(() -> mapper.map(document, "crud"))
                .isInstanceOfSatisfying(FigmaApiException.class, exception -> {
                    assertThat(exception.code()).isEqualTo("FIGMA_UNSUPPORTED_NODE_TYPE");
                    assertThat(exception.statusCode()).isEqualTo(422);
                });
    }

    @Test
    void componentsCaptureFirstSolidFillAndStrokeAsRgba() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[{"type":"SOLID","visible":true,"color":{"r":1,"g":0.341176,"b":0.2}}],
                     "strokes":[{"type":"SOLID","visible":true,"color":{"r":0,"g":0,"b":0}}]}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        var actionGroup = result.components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();
        assertThat(actionGroup.backgroundColor()).isEqualTo("rgba(255,87,51,1.00)");
        assertThat(actionGroup.borderColor()).isEqualTo("rgba(0,0,0,1.00)");
    }

    @Test
    void componentsIgnoreNonSolidFillsLikeGradients() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[{"type":"GRADIENT_LINEAR","visible":true}]}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        var actionGroup = result.components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();
        assertThat(actionGroup.backgroundColor()).isNull();
        assertThat(actionGroup.borderColor()).isNull();
    }

    @Test
    void componentsApplyPaintOpacityAndCompatibleColorAlpha() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[{"type":"SOLID","opacity":0.5,
                               "color":{"r":1,"g":0,"b":0,"a":0.8}}],
                     "strokes":[{"type":"SOLID","opacity":0.5,
                                 "color":{"r":0,"g":0,"b":0}}]}
                  ]
                }
                """));

        var actionGroup = mapper.map(document, "crud").components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();

        assertThat(actionGroup.backgroundColor()).isEqualTo("rgba(255,0,0,0.40)");
        assertThat(actionGroup.borderColor()).isEqualTo("rgba(0,0,0,0.50)");
    }

    @Test
    void geometryPreservesOrderedPaintMetadataSeparatelyFromColorAlpha() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {"type":"FRAME","name":"화면","absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":100},
                 "fills":[{"type":"SOLID","opacity":0.5,"color":{"r":1,"g":0,"b":0,"a":0.8}},
                           {"type":"GRADIENT_LINEAR","visible":false,
                            "gradientStops":[{"position":0,"color":{"r":1,"g":0,"b":0,"a":0.5}},
                                              {"position":1,"color":{"r":0,"g":0,"b":1}}],
                            "gradientHandlePositions":[{"x":0,"y":0.5},{"x":1,"y":0.5}]},
                           {"type":"IMAGE","opacity":0.25,"imageRef":"asset-123","scaleMode":"FILL"}],
                 "strokes":[{"type":"SOLID","color":{"r":0,"g":1,"b":0}}]}
                """));

        var geometry = mapper.map(document, "crud").geometryTree().get(0);

        assertThat(geometry.fills()).extracting(UiDesignSpec.PaintSpec::type)
                .containsExactly("SOLID", "GRADIENT_LINEAR", "IMAGE");
        assertThat(geometry.fills().get(0).color()).isEqualTo("rgba(255,0,0,0.80)");
        assertThat(geometry.fills().get(0).opacity()).isEqualTo(0.5);
        assertThat(geometry.fills().get(1).visible()).isFalse();
        assertThat(geometry.fills().get(1).gradientStops()).extracting(UiDesignSpec.PaintSpec.GradientStop::position)
                .containsExactly(0.0, 1.0);
        assertThat(geometry.fills().get(1).gradientStops().get(0).color()).isEqualTo("rgba(255,0,0,0.50)");
        assertThat(geometry.fills().get(1).gradientHandlePositions()).hasSize(2);
        assertThat(geometry.fills().get(2).color()).isNull();
        assertThat(geometry.fills().get(2).imageRef()).isEqualTo("asset-123");
        assertThat(geometry.fills().get(2).scaleMode()).isEqualTo("FILL");
        assertThat(geometry.strokes()).hasSize(1);

        UiDesignSpec restored = objectMapper.readValue(objectMapper.writeValueAsString(mapper.map(document, "crud")), UiDesignSpec.class);
        assertThat(restored.geometryTree().get(0).fills()).isEqualTo(geometry.fills());
        assertThat(restored.geometryTree().get(0).strokes()).isEqualTo(geometry.strokes());
    }

    @Test
    void componentsClampPaintAndColorAlpha() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[{"type":"SOLID","opacity":2,
                               "color":{"r":1,"g":0,"b":0,"a":2}}],
                     "strokes":[{"type":"SOLID","opacity":-1,
                                 "color":{"r":0,"g":0,"b":0}}]}
                  ]
                }
                """));

        var actionGroup = mapper.map(document, "crud").components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();

        assertThat(actionGroup.backgroundColor()).isEqualTo("rgba(255,0,0,1.00)");
        assertThat(actionGroup.borderColor()).isEqualTo("rgba(0,0,0,0.00)");
    }

    @Test
    void componentsSupplementFirstValidFillAndStrokeIndependently() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"FRAME","name":"Button Group",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":300,"height":40}},
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[{"type":"SOLID","color":{"r":1,"g":0,"b":0}}]},
                    {"type":"COMPONENT","name":"Secondary Button",
                     "absoluteBoundingBox":{"x":240,"y":100,"width":120,"height":40},
                     "fills":[{"type":"SOLID","color":{"r":0,"g":1,"b":0}}],
                     "strokes":[{"type":"SOLID","color":{"r":0,"g":0,"b":1}}]},
                    {"type":"COMPONENT","name":"Tertiary Button",
                     "absoluteBoundingBox":{"x":380,"y":100,"width":120,"height":40},
                     "strokes":[{"type":"SOLID","color":{"r":0,"g":0,"b":0}}]}
                  ]
                }
                """));

        var actionGroup = mapper.map(document, "crud").components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();

        assertThat(actionGroup.backgroundColor()).isEqualTo("rgba(255,0,0,1.00)");
        assertThat(actionGroup.borderColor()).isEqualTo("rgba(0,0,255,1.00)");
    }

    @Test
    void componentsSkipInvisibleAndUnsupportedPaintsBeforeVisibleSolid() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[
                       {"type":"SOLID","visible":false,"color":{"r":1,"g":0,"b":0}},
                       {"type":"GRADIENT_LINEAR","visible":true},
                       {"type":"IMAGE","visible":true},
                       {"type":"SOLID","visible":true,"color":{"r":0,"g":1,"b":0}}
                     ]}
                  ]
                }
                """));

        var actionGroup = mapper.map(document, "crud").components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();

        assertThat(actionGroup.backgroundColor()).isEqualTo("rgba(0,255,0,1.00)");
    }

    @Test
    void componentsIgnoreGradientAndImageOnlyPaints() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40},
                     "fills":[
                       {"type":"GRADIENT_LINEAR","visible":true},
                       {"type":"IMAGE","visible":true}
                     ]}
                  ]
                }
                """));

        var actionGroup = mapper.map(document, "crud").components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();

        assertThat(actionGroup.backgroundColor()).isNull();
        assertThat(actionGroup.borderColor()).isNull();
    }

    @Test
    void geometryTreePreservesNestedCoordinatesAndAutoLayout() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "layoutMode":"HORIZONTAL", "itemSpacing":16,
                  "paddingTop":8, "paddingRight":12, "paddingBottom":8, "paddingLeft":12,
                  "children":[
                    {"id":"1:2","type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":1200,"y":760,"width":120,"height":44},
                     "cornerRadius":8, "opacity":0.9}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        assertThat(result.geometryTree()).hasSize(1);
        UiDesignSpec.NodeGeometry root = result.geometryTree().get(0);
        assertThat(root.nodeId()).isEqualTo("1:1");
        assertThat(root.width()).isEqualTo(1440);
        assertThat(root.autoLayout()).isNotNull();
        assertThat(root.autoLayout().direction()).isEqualTo("HORIZONTAL");
        assertThat(root.autoLayout().itemSpacing()).isEqualTo(16);
        assertThat(root.children()).hasSize(1);
        UiDesignSpec.NodeGeometry button = root.children().get(0);
        assertThat(button.nodeId()).isEqualTo("1:2");
        assertThat(button.x()).isEqualTo(1200);
        assertThat(button.cornerRadius()).isEqualTo(8);
        assertThat(button.opacity()).isEqualTo(0.9);
    }

    @Test
    void geometryTreeCapturesTextStyleForTextNodes() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:3","type":"TEXT","name":"title","characters":"제목",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":100,"height":24},
                     "style":{"fontFamily":"Pretendard","fontSize":16,"fontWeight":700,"lineHeightPx":24}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        UiDesignSpec.NodeGeometry text = result.geometryTree().get(0).children().get(0);
        assertThat(text.textStyle()).isNotNull();
        assertThat(text.textStyle().fontFamily()).isEqualTo("Pretendard");
        assertThat(text.textStyle().fontSize()).isEqualTo(16);
        assertThat(text.textStyle().fontWeight()).isEqualTo(700);
    }

    @Test
    void geometryTreeExcludesInvisibleChildren() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:4","type":"TEXT","name":"hidden","visible":false,
                     "absoluteBoundingBox":{"x":0,"y":0,"width":10,"height":10}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        assertThat(result.geometryTree().get(0).children()).isEmpty();
    }

    @Test
    void geometryTreeCollapsesThreeOrMoreRepeatedSiblings() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:10","type":"FRAME","name":"Row","absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":40}},
                    {"id":"1:11","type":"FRAME","name":"Row","absoluteBoundingBox":{"x":0,"y":40,"width":100,"height":40}},
                    {"id":"1:12","type":"FRAME","name":"Row","absoluteBoundingBox":{"x":0,"y":80,"width":100,"height":40}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        assertThat(result.geometryTree().get(0).children()).hasSize(1);
        assertThat(result.uncertainties()).anyMatch(value -> value.contains("반복 패턴 3개"));
    }

    @Test
    void geometryTreeCollapsesRepeatedSiblingsWithIndexedNamesAndSizeTolerance() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:10","type":"FRAME","name":"Row 1","absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":40}},
                    {"id":"1:11","type":"FRAME","name":"Row 2","absoluteBoundingBox":{"x":0,"y":40,"width":102,"height":40}},
                    {"id":"1:12","type":"FRAME","name":"Row 3","absoluteBoundingBox":{"x":0,"y":80,"width":100,"height":43}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        assertThat(result.geometryTree().get(0).children()).hasSize(1);
        assertThat(result.geometryTree().get(0).children().get(0).nodeId()).isEqualTo("1:10");
        assertThat(result.uncertainties()).anyMatch(value -> value.contains("반복 패턴 3개") && value.contains("Row"));
    }

    @Test
    void geometryTreeCollapsesNonConsecutiveRepeatedSiblings() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:10","type":"FRAME","name":"Row","absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":40}},
                    {"id":"1:20","type":"FRAME","name":"Divider","absoluteBoundingBox":{"x":0,"y":40,"width":100,"height":4}},
                    {"id":"1:11","type":"FRAME","name":"Row","absoluteBoundingBox":{"x":0,"y":44,"width":100,"height":40}},
                    {"id":"1:21","type":"FRAME","name":"Divider","absoluteBoundingBox":{"x":0,"y":84,"width":100,"height":4}},
                    {"id":"1:12","type":"FRAME","name":"Row","absoluteBoundingBox":{"x":0,"y":88,"width":100,"height":40}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        var children = result.geometryTree().get(0).children();
        assertThat(children).extracting(child -> child.name()).containsExactly("Row", "Divider", "Divider");
        assertThat(result.uncertainties()).anyMatch(value -> value.contains("반복 패턴 3개") && value.contains("Row"));
    }

    @Test
    void recognizesNoticeBoardFrameNameWithoutExplicitFeatureType() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {"type":"FRAME", "name":"공지사항 목록",
                 "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900}}
                """));

        var result = mapper.map(document, null);

        assertThat(result.archetype()).isEqualTo("BOARD_LIST");
    }

    @Test
    void recognizesWritingFrameNameAsFormArchetype() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {"type":"FRAME", "name":"게시글 작성",
                 "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900}}
                """));

        var result = mapper.map(document, null);

        assertThat(result.archetype()).isEqualTo("BOARD_FORM");
    }

    @Test
    void recognizesViewingFrameNameAsDetailArchetype() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {"type":"FRAME", "name":"직원 보기",
                 "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900}}
                """));

        var result = mapper.map(document, null);

        assertThat(result.archetype()).isEqualTo("CRUD_DETAIL");
    }

    @Test
    void collectsImageNodeIdsFromVectorAndImageFillNodes() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "id": "1:1", "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"id":"1:2","type":"VECTOR","name":"아이콘",
                     "absoluteBoundingBox":{"x":0,"y":0,"width":24,"height":24}},
                    {"id":"1:3","type":"RECTANGLE","name":"배경사진",
                     "absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":100},
                     "fills":[{"type":"IMAGE","visible":true}]},
                    {"id":"1:4","type":"COMPONENT","name":"버튼",
                     "absoluteBoundingBox":{"x":0,"y":0,"width":100,"height":40},
                     "fills":[{"type":"SOLID","visible":true,"color":{"r":1,"g":0,"b":0,"a":1}}]}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        assertThat(result.imageNodeIds()).containsExactlyInAnyOrder("1:2", "1:3");
    }

    @Test
    void componentsWithoutFillsOrStrokesHaveNullColors() throws Exception {
        var document = new FigmaNodeDocument("v1", objectMapper.readTree("""
                {
                  "type":"FRAME", "name":"목록",
                  "absoluteBoundingBox":{"x":0,"y":0,"width":1440,"height":900},
                  "children":[
                    {"type":"COMPONENT","name":"Primary Button",
                     "absoluteBoundingBox":{"x":100,"y":100,"width":120,"height":40}}
                  ]
                }
                """));

        var result = mapper.map(document, "crud");

        var actionGroup = result.components().stream()
                .filter(component -> "ACTION_GROUP".equals(component.type()))
                .findFirst().orElseThrow();
        assertThat(actionGroup.backgroundColor()).isNull();
        assertThat(actionGroup.borderColor()).isNull();
    }
}
