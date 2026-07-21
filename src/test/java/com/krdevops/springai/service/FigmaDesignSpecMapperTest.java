package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.FigmaNodeDocument;
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
}
