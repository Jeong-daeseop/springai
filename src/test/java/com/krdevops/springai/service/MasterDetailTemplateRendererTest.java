package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MasterDetailTemplateRendererTest {

    @Autowired
    MasterDetailTemplateRenderer renderer;

    private static final FieldModel BBS_ID = new FieldModel(
            "BBS_ID", "bbsId", "String", "게시판ID", true, true, true, 20, "VARCHAR");
    private static final FieldModel BBS_NM = new FieldModel(
            "BBS_NM", "bbsNm", "String", "게시판명", false, true, true, 255, "VARCHAR");
    private static final FieldModel USE_AT = new FieldModel(
            "USE_AT", "useAt", "String", "사용여부", false, true, true, 1, "VARCHAR");
    private static final FieldModel TRGET_ID = new FieldModel(
            "TRGET_ID", "trgetId", "String", "대상ID", true, true, true, 20, "VARCHAR");

    private MasterDetailTemplateModel model() {
        CrudTemplateModel master = new CrudTemplateModel(
                "egovframework.let.bbs",
                "BbsMaster",
                "bbsMaster",
                "게시판마스터",
                "COMTNBBSMASTER",
                "/bbs/bbsMaster",
                "2026-06-26",
                "5.0",
                true,
                new PkModel("BBS_ID", "bbsId", "String"),
                List.of(BBS_ID, BBS_NM, USE_AT),
                List.of(BBS_ID, BBS_NM, USE_AT),
                List.of(BBS_NM, USE_AT)
        );
        CrudTemplateModel detail = new CrudTemplateModel(
                "egovframework.let.bbs",
                "Bbsuse",
                "bbsuse",
                "게시판사용",
                "COMTNBBSUSE",
                "/bbs/bbsuse",
                "2026-06-26",
                "5.0",
                true,
                new PkModel("BBS_ID", "bbsId", "String"),
                List.of(BBS_ID, TRGET_ID, USE_AT),
                List.of(BBS_ID, TRGET_ID, USE_AT),
                List.of(TRGET_ID, USE_AT)
        );
        return new MasterDetailTemplateModel(master, detail, "BBS_ID", "bbsId");
    }

    @Test
    void renderAllLayers_withoutFreeMarkerErrors() {
        List<String> layers = List.of(
                "masterVo", "detailVo", "masterMapper", "detailMapper",
                "masterMapperXml", "detailMapperXml", "service", "serviceImpl",
                "controller", "validationHandler", "jspList", "jspDetail", "jspRegist",
                "layoutHtml", "thymeleafList", "thymeleafDetail", "thymeleafRegist"
        );

        for (String layer : layers) {
            String result = renderer.renderByLayerKey(layer, model());
            assertThat(result).isNotBlank();
            assertThat(result).doesNotContain("<#");
            assertThat(result).doesNotContain("</#");
        }
    }

    @Test
    void controller_loadsDetailListOnMasterDetailView() {
        String result = renderer.renderByLayerKey("controller", model());

        assertThat(result).contains("selectBbsuseList(searchVO.getBbsId())");
        assertThat(result).contains("model.addAttribute(\"detailList\", detailList)");
    }

    @Test
    void detailMapper_usesParamForFkStringBinding() {
        String result = renderer.renderByLayerKey("detailMapper", model());

        assertThat(result).contains("import org.apache.ibatis.annotations.Param;");
        assertThat(result).contains("@Param(\"bbsId\") String bbsId");
    }

    @Test
    void thymeleafDetail_containsDetailGrid() {
        String result = renderer.renderByLayerKey("thymeleafDetail", model());

        assertThat(result).contains("th:each=\"detailItem : ${detailList}\"");
        assertThat(result).contains("등록된 게시판사용 정보가 없습니다.");
    }

    @Test
    void renderByLayerKey_unknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> renderer.renderByLayerKey("unknown", model()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
