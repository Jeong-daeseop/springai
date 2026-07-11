package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.model.crud.PkModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CrudTemplateRenderer — .ftl 파일 로딩 및 렌더링 검증.
 * FreeMarker record accessor 동작과 jakarta/javax 분기를 확인한다.
 */
@SpringBootTest
class CrudTemplateRendererTest {

    @Autowired
    CrudTemplateRenderer renderer;

    // ─── 픽스처 ───────────────────────────────────────────────────────────────

    private static final List<FieldModel> FIELDS = List.of(
        new FieldModel("EMPLYR_ID", "emplyrId", "String",  "직원ID",   true,  true,  true,  20,   "VARCHAR"),
        new FieldModel("USER_NM",   "userNm",   "String",  "사용자명", false, true,  true,  50,   "VARCHAR"),
        new FieldModel("AGE",       "age",       "Integer", "나이",     false, false, false, null, "INTEGER")
    );

    private static final List<FieldModel> NON_PK = FIELDS.stream().filter(f -> !f.pk()).toList();
    private static final List<FieldModel> PK_FIELDS = FIELDS.stream().filter(FieldModel::pk).toList();
    private static final List<FieldModel> LIST_FIELDS = List.of(FIELDS.get(0), FIELDS.get(1));
    private static final PkModel PK = new PkModel("EMPLYR_ID", "emplyrId", "String");

    private CrudTemplateModel model(boolean jakarta) {
        return new CrudTemplateModel(
            "egovframework.let.emp",
            "Employer",
            "employer",
            "직원",
            "COMTNEMPLYRINFO",
            "/emp/employer",
            "2026-06-15",
            jakarta ? "5.0" : "4.3",
            jakarta,
            PK,
            PK_FIELDS,
            FIELDS,
            LIST_FIELDS,
            NON_PK,
            NON_PK
        );
    }

    // ─── VO 렌더링 ────────────────────────────────────────────────────────────

    @Test
    void vo_jakarta50_containsJakartaImport() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("import jakarta.validation.constraints.NotBlank;");
        assertThat(result).contains("import jakarta.validation.constraints.NotNull;");
        assertThat(result).doesNotContain("import javax.validation");
    }

    @Test
    void vo_javax43_containsJavaxImport() {
        String result = renderer.renderByLayerKey("vo", model(false));

        assertThat(result).contains("import javax.validation.constraints.NotBlank;");
        assertThat(result).doesNotContain("import jakarta.validation");
    }

    @Test
    void vo_containsLombokAndPaginationInfo() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("@Getter");
        assertThat(result).contains("@Setter");
        assertThat(result).contains("PaginationInfo paginationInfo");
    }

    @Test
    void vo_fieldsRendered_containsAllJavaFieldDeclarations() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("private String emplyrId;");
        assertThat(result).contains("private String userNm;");
        assertThat(result).contains("private Integer age;");
    }

    @Test
    void vo_requiredStringField_containsNotBlank() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("@NotBlank");
    }

    @Test
    void vo_maxLength_containsSizeAnnotation() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).contains("@Size(max = 50)");
    }

    @Test
    void vo_noUnresolvedFreeMarkerSyntax() {
        String result = renderer.renderByLayerKey("vo", model(true));

        assertThat(result).doesNotContain("<#");
        assertThat(result).doesNotContain("${");
    }

    // ─── Controller 렌더링 ────────────────────────────────────────────────────

    @Test
    void controller_jakarta50_containsJakartaValidImport() {
        String result = renderer.renderByLayerKey("controller", model(true));

        assertThat(result).contains("import jakarta.validation.Valid;");
        assertThat(result).doesNotContain("import javax.validation.Valid");
    }

    @Test
    void controller_javax43_containsJavaxValidImport() {
        String result = renderer.renderByLayerKey("controller", model(false));

        assertThat(result).contains("import javax.validation.Valid;");
        assertThat(result).doesNotContain("import jakarta.validation.Valid");
    }

    @Test
    void controller_containsRequiredAnnotationsAndClasses() {
        String result = renderer.renderByLayerKey("controller", model(true));

        assertThat(result).contains("@Controller");
        assertThat(result).contains("EgovPropertyService");
        assertThat(result).contains("ModelMap");
        assertThat(result).contains("PaginationInfo");
    }

    @Test
    void controller_delegatesLayoutModelToProgramListInterceptor() {
        String result = renderer.renderByLayerKey("controller", model(true));

        assertThat(result).contains("populateLayoutModel(model, \"crud-list\")");
        assertThat(result).contains("LETTNPROGRMLIST.URL과 현재 요청 경로를 매칭");
        assertThat(result).contains("model.addAttribute(\"currentMenuId\", currentMenuId)");
        assertThat(result).doesNotContain("model.addAttribute(\"lnbTitle\"");
        assertThat(result).doesNotContain("model.addAttribute(\"lnbMenus\"");
        assertThat(result).doesNotContain("model.addAttribute(\"breadcrumbs\"");
    }

    // ─── Mapper XML 렌더링 ────────────────────────────────────────────────────

    @Test
    void mapperXml_containsSixSqlIds() {
        String result = renderer.renderByLayerKey("mapperXml", model(true));

        assertThat(result).contains("id=\"selectEmployerList\"");
        assertThat(result).contains("id=\"selectEmployerListTotCnt\"");
        assertThat(result).contains("id=\"selectEmployer\"");
        assertThat(result).contains("id=\"insertEmployer\"");
        assertThat(result).contains("id=\"updateEmployer\"");
        assertThat(result).contains("id=\"deleteEmployer\"");
    }

    @Test
    void mapperXml_containsPaginationFirstRecordIndex() {
        String result = renderer.renderByLayerKey("mapperXml", model(true));

        assertThat(result).contains("paginationInfo.firstRecordIndex");
        assertThat(result).contains("paginationInfo.recordCountPerPage");
    }

    @Test
    void mapperXml_containsResultMap() {
        String result = renderer.renderByLayerKey("mapperXml", model(true));

        assertThat(result).contains("<resultMap");
        assertThat(result).contains("<id property=\"emplyrId\" column=\"EMPLYR_ID\"");
        assertThat(result).contains("<result property=\"userNm\" column=\"USER_NM\"");
    }

    // ─── thymeleafList — decorate/standalone 분리(body include) 검증 ─────────

    @Test
    void thymeleafList_decorate_includesBodyAndKeepsLayoutReferences() {
        String result = renderer.renderByLayerKey(
                "thymeleafList", model(true), "layout/default", "layout/breadcrumb", "layout");

        assertThat(result)
                .contains("layout:decorate=\"~{layout/default}\"")
                .contains("직원 목록")
                .contains("krds-table-wrap")
                .doesNotContain("~{layout/breadcrumb :: breadcrumb}")
                .doesNotContain("<#include");
    }

    @Test
    void thymeleafList_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafList", model(true), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("krds-table-wrap")
                .contains("직원 목록")
                .doesNotContain("layout:decorate")
                .doesNotContain("xmlns:layout")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    @Test
    void thymeleafDetail_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafDetail", model(true), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("직원 상세")
                .doesNotContain("layout:decorate")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    @Test
    void thymeleafRegist_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafRegist", model(true), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("직원 등록")
                .doesNotContain("layout:decorate")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    @Test
    void thymeleafUpdt_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafUpdt", model(true), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("직원 수정")
                .doesNotContain("layout:decorate")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    // ─── 화면 h1 제목 — 공통 CSS 클래스 기준으로 동일해야 함 ──────────────────

    @Test
    void thymeleafDetailRegistUpdt_h1TitleUsesCommonPageTitleClass() {
        String detail = renderer.renderByLayerKey("thymeleafDetail", model(true));
        String regist = renderer.renderByLayerKey("thymeleafRegist", model(true));
        String updt = renderer.renderByLayerKey("thymeleafUpdt", model(true));

        assertThat(detail).contains("<h1 class=\"egov-page-title\">직원 상세</h1>");
        assertThat(regist).contains("<h1 class=\"egov-page-title\">직원 등록</h1>");
        assertThat(updt).contains("<h1 class=\"egov-page-title\">직원 수정</h1>");
        assertThat(detail).doesNotContain("style=\"");
        assertThat(regist).doesNotContain("style=\"");
        assertThat(updt).doesNotContain("style=\"");
    }

    // ─── 알 수 없는 layerKey ─────────────────────────────────────────────────

    @Test
    void renderByLayerKey_unknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> renderer.renderByLayerKey("unknown", model(true)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("unknown");
    }

    // ─── GNB 동적 렌더링 (gnb.html.ftl) ──────────────────────────────────────

    @Test
    void layoutGnbHtml_containsDynamicMenuLoopAndHomeStaysStatic() {
        String result = renderer.renderLayoutByLayerKey("layoutGnbHtml", "layout");

        assertThat(result)
            .contains("th:each=\"menu : ${gnbMenus}\"")
            .contains("th:if=\"${menu.url != null}\"")
            .contains("th:href=\"@{${!#lists.isEmpty(menu.children) ? (!#lists.isEmpty(menu.children[0].children) ? menu.children[0].children[0].url : menu.children[0].url) : menu.url}}\"")
            .contains("th:text=\"${menu.menuNm}\"")
            .contains("th:classappend=\"${menu.menuNo == currentTopMenuNo} ? 'gnb-active'\"")
            .contains("egov-dropdown-inner")
            .contains("egov-dropdown-side")
            .contains("egov-dropdown-content")
            .contains("egov-dropdown-link")
            .doesNotContain("egov-mega-summary")
            .doesNotContain("관련 업무 화면으로 바로 이동합니다.")
            .contains(">홈</a>")
            .doesNotContain("lnbMenus[0]")
            .doesNotContain("시스템관리")
            .doesNotContain("고객지원");
    }

    @Test
    void layoutGnbHtml_dropdownGroupsAreIndexedAndSwitchedByHoverJs() {
        String result = renderer.renderLayoutByLayerKey("layoutGnbHtml", "layout");

        assertThat(result)
            .contains("th:each=\"child, cStat : ${menu.children}\"")
            .contains("th:attr=\"data-dd-idx=${cStat.index}\"")
            .contains("mouseenter")
            .contains("is-active")
            .doesNotContain("menu.children.?[#lists.isEmpty(children)]");
    }

    @Test
    void layoutGnbHtml_topTabLinkDrillsDownToRealLeafWhenFirstChildIsCategory() {
        String result = renderer.renderLayoutByLayerKey("layoutGnbHtml", "layout");

        assertThat(result)
            .contains("!#lists.isEmpty(menu.children[0].children) ? menu.children[0].children[0].url : menu.children[0].url");
    }

    @Test
    void layoutLnbHtml_rendersNestedLeafChildrenWithActiveState() {
        String result = renderer.renderLayoutByLayerKey("layoutLnbHtml", "layout");

        assertThat(result)
            .contains("th:each=\"child : ${menu.children}\"")
            .contains("class=\"lnb-link lnb-sublink\"")
            .contains("th:classappend=\"${child.menuId == currentMenuId} ? 'lnb-active'\"");
    }

    @Test
    void layoutDefaultHtml_activeTabIndicatorOnlyOnGnbActiveClass() {
        String result = renderer.renderLayoutByLayerKey("layoutHtml", "layout");

        assertThat(result)
            .contains("@{/resources/css/styles.css}")
            .contains("class=\"egov-layout-shell\"")
            .contains("th:if=\"${mainPath != true}\"")
            .contains("th:classappend=\"${mainPath == true} ? ' egov-main-shell'\"")
            .contains("th:classappend=\"${mainPath == true} ? ' egov-main-content'\"")
            .doesNotContain("<style>")
            .doesNotContain("style=\"")
            .doesNotContain("border-top: 3px solid #256ef4;");
    }

    @Test
    void stylesCss_containsGnbActiveIndicatorClass() throws Exception {
        String css = Files.readString(Path.of("src/main/resources/templates/egov/styles.css.tpl"));

        assertThat(css)
            .contains(".egov-main-menu-link.gnb-active")
            .contains(".egov-layout-shell.egov-main-shell")
            .contains(".egov-layout-shell:has(.egov-main-dashboard)")
            .contains(".egov-layout-shell:has(.egov-main-dashboard) .egov-lnb")
            .contains(".egov-main-dashboard")
            .contains(".egov-main-metrics")
            .contains("background: #eef3fe;")
            .contains("box-shadow: inset 0 -3px 0 #083891;");
    }

    // ─── GNB 메뉴 컴포넌트 (VO/Mapper/MapperXml/Interceptor) ──────────────────

    @Test
    void renderGnbMenuComponent_vo_containsPackageAndFields() {
        String result = renderer.renderGnbMenuComponent("layoutGnbMenuVo", "egovframework.let.emp");

        assertThat(result)
            .contains("package egovframework.let.emp.cmm.vo;")
            .contains("private Long menuNo;")
            .contains("private String menuNm;")
            .contains("private Integer menuOrdr;")
            .contains("private String url;");
    }

    @Test
    void renderGnbMenuComponent_mapper_containsPackageAndParamAnnotation() {
        String result = renderer.renderGnbMenuComponent("layoutGnbMenuMapper", "egovframework.let.emp");

        assertThat(result)
            .contains("package egovframework.let.emp.cmm.service;")
            .contains("import egovframework.let.emp.cmm.vo.GnbMenuVO;")
            .contains("@Param(\"upperMenuNo\")");
    }

    @Test
    void renderGnbMenuComponent_mapperXml_containsPkFirstResultMapAndUrlFilter() {
        String result = renderer.renderGnbMenuComponent("layoutGnbMenuMapperXml", "egovframework.let.emp");

        assertThat(result)
            .contains("namespace=\"egovframework.let.emp.cmm.service.GnbMenuMapper\"")
            .contains("<id property=\"menuNo\" column=\"MENU_NO\"/>")
            .contains("FROM LETTNMENUINFO m")
            .contains("LEFT JOIN LETTNPROGRMLIST p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM")
            .contains("WHERE m.UPPER_MENU_NO = #{upperMenuNo}")
            .contains("AND m.MENU_NO != 0");
        int idIndex = result.indexOf("<id property=\"menuNo\"");
        int resultIndex = result.indexOf("<result property=\"menuNm\"");
        assertThat(idIndex).isLessThan(resultIndex);
    }

    @Test
    void renderGnbMenuComponent_mapperXml_usesQualifiedTableNamesWhenProvided() {
        String result = renderer.renderGnbMenuComponent(
                "layoutGnbMenuMapperXml",
                "egovframework.let.emp",
                "LETTNMENUINFO",
                "LETTNPROGRMLIST");

        assertThat(result)
            .contains("FROM LETTNMENUINFO m")
            .contains("LEFT JOIN LETTNPROGRMLIST p ON m.PROGRM_FILE_NM = p.PROGRM_FILE_NM");
    }

    @Test
    void renderGnbMenuComponent_interceptor_containsSkipConditionsAndServletPathMatching() {
        String result = renderer.renderGnbMenuComponent("layoutGnbMenuInterceptor", "egovframework.let.emp");

        assertThat(result)
            .contains("package egovframework.let.emp.cmm.web;")
            .contains("implements HandlerInterceptor")
            .contains("request.getServletPath()")
            .contains("\"/api/\"")
            .contains("\"/mcp/\"")
            .contains("\"/ai/\"")
            .contains("gnbMenuMapper.selectGnbMenuList(0L)")
            .contains("boolean mainPath = isMainPath(servletPath)")
            .contains("if (currentTopMenuNo == null && !mainPath)")
            .contains("modelAndView.addObject(\"mainPath\", mainPath)")
            .contains("\"/com/main.do\".equals(servletPath)")
            .contains("\"/egovframework/com/main.do\".equals(servletPath)")
            .contains("currentTopMenuNo = defaultTopMenuNo(gnbMenus)")
            .contains("populateBreadcrumbModel(modelAndView, servletPath, gnbMenus, currentTopMenuNo)")
            .contains("breadcrumbs.add(crumb(\"홈\", \"/\"))");
    }
}
