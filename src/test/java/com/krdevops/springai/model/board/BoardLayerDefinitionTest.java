package com.krdevops.springai.model.board;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoardLayerDefinitionTest {

    @Test
    void thymeleafLayers_total_17() {
        assertThat(BoardLayerDefinition.THYMELEAF_LAYERS).hasSize(17);
    }

    @Test
    void jspLayers_total_12() {
        assertThat(BoardLayerDefinition.JSP_LAYERS).hasSize(12);
    }

    @Test
    void thymeleafLayers_containsLayoutHtml() {
        List<String> keys = BoardLayerDefinition.THYMELEAF_LAYERS.stream()
                .map(BoardLayerDefinition::layerKey)
                .toList();
        assertThat(keys).contains("layoutHtml", "layoutGnbHtml", "layoutLnbHtml",
                "layoutBreadcrumbHtml", "layoutFooterHtml");
    }

    @Test
    void thymeleafLayers_layoutHtml_isFirstViewLayer() {
        // COMMON_LAYERS 8개 다음 첫 번째가 layoutHtml
        List<String> keys = BoardLayerDefinition.THYMELEAF_LAYERS.stream()
                .map(BoardLayerDefinition::layerKey)
                .toList();
        assertThat(keys.get(8)).isEqualTo("layoutHtml");
    }

    @Test
    void resolveFileName_layoutHtml_returnsFixedPath() {
        assertThat(BoardLayerDefinition.resolveFileName("layoutHtml", "Bbs", "layout/default.html"))
                .isEqualTo("layout/default.html");
        assertThat(BoardLayerDefinition.resolveFileName("layoutGnbHtml", "Bbs", "layout/gnb.html"))
                .isEqualTo("layout/gnb.html");
        assertThat(BoardLayerDefinition.resolveFileName("layoutLnbHtml", "Bbs", "layout/lnb.html"))
                .isEqualTo("layout/lnb.html");
        assertThat(BoardLayerDefinition.resolveFileName("layoutBreadcrumbHtml", "Bbs", "layout/breadcrumb.html"))
                .isEqualTo("layout/breadcrumb.html");
        assertThat(BoardLayerDefinition.resolveFileName("layoutFooterHtml", "Bbs", "layout/footer.html"))
                .isEqualTo("layout/footer.html");
    }

    @Test
    void resolveFileName_voAndService_noPrefixEgov() {
        assertThat(BoardLayerDefinition.resolveFileName("vo",        "Bbs", "VO.java")).isEqualTo("BbsVO.java");
        assertThat(BoardLayerDefinition.resolveFileName("searchVo",  "Bbs", "SearchVO.java")).isEqualTo("BbsSearchVO.java");
        assertThat(BoardLayerDefinition.resolveFileName("mapper",    "Bbs", "Mapper.java")).isEqualTo("BbsMapper.java");
        assertThat(BoardLayerDefinition.resolveFileName("mapperXml", "Bbs", "Mapper.xml")).isEqualTo("BbsMapper.xml");
        assertThat(BoardLayerDefinition.resolveFileName("service",   "Bbs", "Service.java")).isEqualTo("BbsService.java");
    }

    @Test
    void resolveFileName_controllerAndOthers_hasPrefixEgov() {
        assertThat(BoardLayerDefinition.resolveFileName("serviceImpl",  "Bbs", "ServiceImpl.java"))
                .isEqualTo("EgovBbsServiceImpl.java");
        assertThat(BoardLayerDefinition.resolveFileName("controller",   "Bbs", "Controller.java"))
                .isEqualTo("EgovBbsController.java");
        assertThat(BoardLayerDefinition.resolveFileName("validHandler", "Bbs", "ValidationHandler.java"))
                .isEqualTo("EgovBbsValidationHandler.java");
    }

    @Test
    void layoutHtml_subPath_isTemplatesRoot() {
        BoardLayerDefinition layout = BoardLayerDefinition.THYMELEAF_LAYERS.stream()
                .filter(l -> "layoutHtml".equals(l.layerKey()))
                .findFirst().orElseThrow();
        assertThat(layout.resolveSubPath("bbs", "bbs"))
                .isEqualTo("src/main/resources/templates/");
    }

    @Test
    void thymeleafLayers_viewKeys_containsAllFourScreens() {
        List<String> keys = BoardLayerDefinition.THYMELEAF_LAYERS.stream()
                .map(BoardLayerDefinition::layerKey)
                .toList();
        assertThat(keys).contains("thymeleafList", "thymeleafDetail", "thymeleafRegist", "thymeleafUpdt");
    }
}
