package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudLayoutMode;
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
                List.of(BBS_ID),
                List.of(BBS_ID, BBS_NM, USE_AT),
                List.of(BBS_ID, BBS_NM, USE_AT),
                List.of(BBS_NM, USE_AT),
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
                List.of(BBS_ID),
                List.of(BBS_ID, TRGET_ID, USE_AT),
                List.of(BBS_ID, TRGET_ID, USE_AT),
                List.of(TRGET_ID, USE_AT),
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
                "layoutHtml", "layoutGnbHtml", "layoutLnbHtml", "layoutBreadcrumbHtml",
                "layoutFooterHtml", "thymeleafList", "thymeleafDetail", "thymeleafRegist"
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
    void controller_populatesLayoutModelContract() {
        String result = renderer.renderByLayerKey("controller", model());

        assertThat(result).contains("populateLayoutModel(model, \"masterdetail-list\", \"게시판마스터 목록\")");
        assertThat(result).contains("model.addAttribute(\"lnbTitle\", \"게시판마스터 관리\")");
        assertThat(result).contains("model.addAttribute(\"lnbMenus\"");
        assertThat(result).contains("model.addAttribute(\"breadcrumbs\", breadcrumbs)");
        assertThat(result).contains("model.addAttribute(\"currentMenuId\", currentMenuId)");
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
    void thymeleafDetailAndRegist_h1TitleUsesCommonClass() {
        String detail = renderer.renderByLayerKey("thymeleafDetail", model());
        String regist = renderer.renderByLayerKey("thymeleafRegist", model());

        assertThat(detail).contains("<h1 class=\"egov-page-title\">");
        assertThat(regist).contains("<h1 class=\"egov-page-title\">");
        assertThat(detail).doesNotContain("style=\"");
        assertThat(regist).doesNotContain("style=\"");
    }

    // ─── layoutMode=none — 독립 화면 템플릿(<th:block> 구조 유지) ───────────

    @Test
    void thymeleafList_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafList", model(), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("게시판마스터 목록")
                .doesNotContain("layout:decorate")
                .doesNotContain("xmlns:layout")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    @Test
    void thymeleafDetail_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafDetail", model(), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("등록된 게시판사용 정보가 없습니다.")
                .doesNotContain("layout:decorate")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    @Test
    void thymeleafRegist_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafRegist", model(), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .doesNotContain("layout:decorate")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    @Test
    void renderByLayerKey_unknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> renderer.renderByLayerKey("unknown", model()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    // ─── GNB 동적 렌더링 (gnb.html.ftl) ──────────────────────────────────────

    @Test
    void layoutGnbHtml_containsDynamicMenuLoopAndHomeStaysStatic() {
        String result = renderer.renderByLayerKey("layoutGnbHtml", model());

        assertThat(result)
                .contains("th:each=\"menu : ${gnbMenus}\"")
                .contains("th:if=\"${menu.url != null}\"")
                .contains("th:href=\"@{${menu.url}}\"")
                .contains(">홈</a>")
                .doesNotContain("lnbMenus[0]")
                .doesNotContain("시스템관리")
                .doesNotContain("고객지원");
    }
}
