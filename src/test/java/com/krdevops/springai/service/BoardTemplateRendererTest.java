package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.board.BoardDisplayModel;
import com.krdevops.springai.model.board.BoardRouteModel;
import com.krdevops.springai.model.crud.CrudLayoutMode;
import com.krdevops.springai.model.crud.FieldModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BoardTemplateRenderer — FTL 로딩 및 렌더링 검증.
 * boardFreemarkerConfiguration 빈과 실제 FTL 파일을 사용한다 (CrudTemplateRendererTest 패턴 동일).
 */
@SpringBootTest
class BoardTemplateRendererTest {

    @Autowired
    BoardTemplateRenderer renderer;

    private static final FieldModel BBS_ID = new FieldModel(
            "BBS_ID", "bbsId", "String", "게시판ID", true, false, false, 20, "VARCHAR");
    private static final FieldModel NTT_ID = new FieldModel(
            "NTT_ID", "nttId", "Long", "게시글번호", true, false, false, null, "BIGINT");
    private static final FieldModel NTT_SJ = new FieldModel(
            "NTT_SJ", "nttSj", "String", "제목", false, true, true, 255, "VARCHAR");
    private static final FieldModel NTT_CN = new FieldModel(
            "NTT_CN", "nttCn", "String", "내용", false, false, true, 2000, "VARCHAR");
    private static final FieldModel NOTICE_AT = new FieldModel(
            "NOTICE_AT", "noticeAt", "String", "공지여부", false, false, true, 1, "VARCHAR");
    private static final FieldModel ATCH_FILE_ID = new FieldModel(
            "ATCH_FILE_ID", "atchFileId", "String", "첨부파일ID", false, false, true, 20, "VARCHAR");
    private static final FieldModel USE_AT = new FieldModel(
            "USE_AT", "useAt", "String", "사용여부", false, true, true, 1, "VARCHAR");

    private BoardTemplateModel model() {
        return new BoardTemplateModel(
                "egovframework.let.bbs",
                "Bbs",
                "bbs",
                "BBS",
                "LETTNBBS",
                "LETTNBBSMASTER",
                "LETTNBBSUSE",
                "/bbs/bbs",
                "2026-06-22",
                "5.0",
                true,
                BBS_ID,
                NTT_ID,
                false,
                null,
                null,
                List.of(BBS_ID, NTT_ID, NTT_SJ, NTT_CN),
                List.of(NTT_SJ),
                List.of(BBS_ID, NTT_ID, NTT_SJ, NTT_CN),
                List.of(NTT_SJ, NTT_CN),
                List.of(),
                false
        );
    }

    private BoardTemplateModel modelWithNoticeAndFile() {
        return new BoardTemplateModel(
                "egovframework.let.bbs",
                "Bbs",
                "bbs",
                "BBS",
                "LETTNBBS",
                "LETTNBBSMASTER",
                "LETTNBBSUSE",
                "/bbs/bbs",
                "2026-06-22",
                "5.0",
                true,
                BBS_ID,
                NTT_ID,
                true,
                ATCH_FILE_ID,
                "LETTNFILEDETAIL",
                List.of(BBS_ID, NTT_ID, NOTICE_AT, NTT_SJ, NTT_CN, ATCH_FILE_ID),
                List.of(NOTICE_AT, NTT_SJ),
                List.of(BBS_ID, NTT_ID, NOTICE_AT, NTT_SJ, NTT_CN, ATCH_FILE_ID),
                List.of(NTT_SJ, NTT_CN, ATCH_FILE_ID),
                List.of(),
                true
        );
    }

    private BoardTemplateModel modelWithVisibility() {
        return new BoardTemplateModel(
                "egovframework.let.bbs", "Bbs", "bbs", "BBS",
                "LETTNBBS", "LETTNBBSMASTER", null, "/bbs/bbs",
                "2026-06-22", "5.0", true, BBS_ID, NTT_ID,
                false, null, null,
                List.of(BBS_ID, NTT_ID, NTT_SJ, NTT_CN, USE_AT),
                List.of(NTT_SJ), List.of(BBS_ID, NTT_ID, NTT_SJ, NTT_CN, USE_AT),
                List.of(NTT_SJ, NTT_CN, USE_AT), List.of(), false);
    }

    // ─── layoutHtml ───────────────────────────────────────────────────────────

    @Test
    void layoutHtml_rendersWithoutFreeMarkerSyntax() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).doesNotContain("<#");
        assertThat(result).contains("egov-");
        assertThat(result).contains("layout/gnb");
        assertThat(result).contains("layout/lnb");
        assertThat(result).contains("layout/footer");
    }

    @Test
    void listSupportsRequestedPageUnitAndFormHasCharacterCounter() {
        assertThat(renderer.renderByLayerKey("controller", model()))
                .contains("requestedPageUnit != 10")
                .contains("searchVO.setPageUnit(requestedPageUnit)");
        assertThat(renderer.renderByLayerKey("thymeleafList", model()))
                .contains("name=\"pageUnit\"")
                .contains(">20개</option>");
        assertThat(renderer.renderByLayerKey("thymeleafRegist", model()))
                .contains("class=\"egov-char-count\"")
                .contains("maxlength=\"2000\"");
        assertThat(renderer.renderByLayerKey("thymeleafUpdt", modelWithVisibility()))
                .contains("role=\"radiogroup\"")
                .contains("th:name=\"useAt\"")
                .contains("사용")
                .contains("미사용");
    }

    @Test
    void layoutHtml_containsContentFragment() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).contains("layout:fragment=\"content\"");
    }

    @Test
    void layoutHtml_containsEgovFrameLayoutShell() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).contains("class=\"egov-layout-shell\"");
        assertThat(result).contains("layout:fragment=\"content\"");
        assertThat(result).contains("class=\"egov-layout-content\"");
        assertThat(result).doesNotContain("style=\"");
        assertThat(result).doesNotContain("<style>");
    }

    @Test
    void layoutHtml_linksStylesCssAndKrdsScript() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).contains("@{/resources/css/styles.css}");
        assertThat(result).contains("@{/resources/js/krds.min.js}");
        assertThat(result).doesNotContain("krds.min.css");
        assertThat(result).doesNotContain("egov-layout.css");
    }

    @Test
    void layoutHtml_containsKrdsGnbStructure() {
        String result = renderer.renderByLayerKey("layoutGnbHtml", model());

        assertThat(result).contains("gnb-main-trigger");
        assertThat(result).contains("egov-main-menu-link");
        assertThat(result).contains("egov-");
    }

    @Test
    void layoutHtml_doesNotDependOnCustomGnbScript() {
        String result = renderer.renderByLayerKey("layoutGnbHtml", model());

        assertThat(result).doesNotContain("addEventListener('pointerenter'");
        assertThat(result).doesNotContain("openMain(trigger)");
        assertThat(result).doesNotContain("openSub(trigger)");
        assertThat(result).contains("egov-main-menu");
    }

    // ─── mapper ───────────────────────────────────────────────────────────────

    @Test
    void mapper_containsMapperAnnotation() {
        String result = renderer.renderByLayerKey("mapper", model());

        assertThat(result).contains("import org.apache.ibatis.annotations.Mapper;");
        assertThat(result).contains("@Mapper");
    }

    @Test
    void controller_populatesLayoutModelContract() {
        String result = renderer.renderByLayerKey("controller", model());

        assertThat(result).contains("populateLayoutModel(model, \"board-list\", \"목록\", searchVO.getBbsId())");
        assertThat(result).contains("model.addAttribute(\"currentPageSuffix\", currentPageSuffix)");
        assertThat(result).contains("model.addAttribute(\"menuContextUrl\", \"/bbs/bbsList.do\")");
        assertThat(result).contains("model.addAttribute(\"lnbMenus\"");
        assertThat(result).contains("model.addAttribute(\"currentMenuId\", currentMenuId)");
        assertThat(result).contains("model.addAttribute(\"resolvedBbsId\", bbsId)");
        assertThat(result)
                .doesNotContain("model.addAttribute(\"lnbTitle\"")
                .doesNotContain("model.addAttribute(\"breadcrumbs\"")
                .doesNotContain("소식·뉴스");
    }

    @Test
    void detailGeneration_separatesReadOnlySelectFromReadCountUpdate() {
        String service = renderer.renderByLayerKey("service", model());
        String serviceImpl = renderer.renderByLayerKey("serviceImpl", model());
        String controller = renderer.renderByLayerKey("controller", model());

        assertThat(service).contains("void updateBbsReadCount(BbsVO vo) throws Exception;");
        assertThat(serviceImpl)
                .contains("@Transactional(readOnly = true)\n    public BbsVO selectBbs(BbsVO vo)")
                .contains("return bbsMapper.selectBbs(vo);")
                .contains("@Transactional\n    public void updateBbsReadCount(BbsVO vo)")
                .contains("bbsMapper.updateReadCount(vo);")
                .doesNotContain("BbsVO result = bbsMapper.selectBbs(vo)");
        assertThat(controller)
                .contains("if (!hasCompositeKey(searchVO))")
                .contains("if (vo == null)")
                .contains("bbsService.updateBbsReadCount(searchVO);")
                .containsSubsequence(
                        "BbsVO vo = bbsService.selectBbs(searchVO);",
                        "if (vo == null)",
                        "bbsService.updateBbsReadCount(searchVO);",
                        "model.addAttribute(\"result\", vo)");
    }

    // ─── vo ───────────────────────────────────────────────────────────────────

    @Test
    void vo_sizeMax_usesPlainNumberWithoutThousandsSeparator() {
        String result = renderer.renderByLayerKey("vo", model());

        assertThat(result).contains("@Size(max = 2000)");
        assertThat(result).doesNotContain("@Size(max = 2,000)");
    }

    @Test
    void vo_generatedNttIdIsNotRejectedBeforeServiceAssignsIt() {
        String result = renderer.renderByLayerKey("vo", model());

        assertThat(result)
                .contains("private Long nttId;")
                .doesNotContain("@NotNull\n    private Long nttId;");
    }

    @Test
    void numericNttIdIsAllocatedByLockedMapperQuery() {
        String mapper = renderer.renderByLayerKey("mapper", model());
        String mapperXml = renderer.renderByLayerKey("mapperXml", model());
        String serviceImpl = renderer.renderByLayerKey("serviceImpl", model());

        assertThat(mapper).contains("Long selectNextBbsNttId();");
        assertThat(mapperXml)
                .contains("<select id=\"selectNextBbsNttId\" resultType=\"Long\">")
                .contains("SELECT COALESCE(MAX(NTT_ID), 0) + 1")
                .contains("FOR UPDATE");
        assertThat(serviceImpl)
                .contains("vo.setNttId(bbsMapper.selectNextBbsNttId());")
                .doesNotContain("new java.math.BigDecimal(nextNttId)")
                .doesNotContain("private EgovIdGnrService egovIdGnrService;");
    }

    // ─── thymeleafList ────────────────────────────────────────────────────────

    @Test
    void thymeleafList_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafList", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
    }

    @Test
    void thymeleafList_containsContentFragment() {
        String result = renderer.renderByLayerKey("thymeleafList", model());

        assertThat(result).contains("layout:fragment=\"content\"");
    }

    @Test
    void thymeleafList_doesNotContainStandaloneBodyOrMain() {
        String result = renderer.renderByLayerKey("thymeleafList", model());

        assertThat(result).doesNotContain("<body>");
        assertThat(result).doesNotContain("<main>");
    }

    @Test
    void thymeleafList_doesNotContainFreeMarkerSyntax() {
        String result = renderer.renderByLayerKey("thymeleafList", model());

        assertThat(result).doesNotContain("<#list");
        assertThat(result).doesNotContain("</#list>");
        assertThat(result).contains("검색어를 입력하세요");
        assertThat(result).contains("layout:decorate");
    }

    @Test
    void thymeleafList_withNoticeAndFile_containsDesignTemplateElements() {
        String result = renderer.renderByLayerKey("thymeleafList", modelWithNoticeAndFile());

        assertThat(result).contains("공지");
        assertThat(result).contains("첨부");
        assertThat(result).contains("첨부파일이 있는 게시글 보기");
        assertThat(result).contains("Detail.do");
        assertThat(result).contains("검색 결과가 없습니다.");
        assertThat(result).doesNotContain("<#if");
        assertThat(result).doesNotContain("FileDownload.do");
    }

    // ─── thymeleafDetail ─────────────────────────────────────────────────────

    @Test
    void thymeleafDetail_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafDetail", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
        assertThat(result).contains("layout:fragment=\"content\"");
        assertThat(result).contains("<h1 class=\"egov-page-title\">");
        assertThat(result).doesNotContain("style=\"");
    }

    @Test
    void thymeleafDetail_withFile_containsAttachmentArea() {
        String result = renderer.renderByLayerKey("thymeleafDetail", modelWithNoticeAndFile());

        assertThat(result).contains("첨부파일 다운로드");
        assertThat(result).contains("/cmm/fms/FileDown.do");
        assertThat(result).contains("file.originalFileName");
        assertThat(result).contains("첨부파일 없음");
        assertThat(result).contains("egov-attachment-box");
        assertThat(result).doesNotContain("style=\"");
    }

    @Test
    void fileDetailListIsGeneratedAcrossBackendLayers() {
        BoardTemplateModel model = modelWithNoticeAndFile();

        assertThat(renderer.renderByLayerKey("mapper", model))
                .contains("selectFileList(@Param(\"atchFileId\") String atchFileId)");
        assertThat(renderer.renderByLayerKey("service", model))
                .contains("List<Map<String, Object>> selectFileList(String atchFileId)");
        assertThat(renderer.renderByLayerKey("serviceImpl", model))
                .contains("bbsMapper.selectFileList(atchFileId)");
        assertThat(renderer.renderByLayerKey("controller", model))
                .contains("model.addAttribute(\"fileList\"")
                .contains("selectFileList(vo.getAtchFileId())");
        assertThat(renderer.renderByLayerKey("mapperXml", model))
                .contains("<select id=\"selectFileList\" resultType=\"map\">")
                .contains("ORIGNL_FILE_NM AS originalFileName")
                .contains("FROM LETTNFILEDETAIL");
    }

    @Test
    void thymeleafDetail_deleteFormContainsConditionalCsrf() {
        assertThat(renderer.renderByLayerKey("thymeleafDetail", model()))
                .contains("th:if=\"${_csrf != null}\"")
                .contains("th:name=\"${_csrf.parameterName}\"")
                .contains("th:value=\"${_csrf.token}\"");
    }

    // ─── thymeleafRegist ─────────────────────────────────────────────────────

    @Test
    void thymeleafRegist_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafRegist", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
        assertThat(result).contains("layout:fragment=\"content\"");
        assertThat(result).contains("<h1 class=\"egov-page-title\">");
        assertThat(result).doesNotContain("style=\"");
    }

    @Test
    void thymeleafRegist_rendersNttCnAsTextarea() {
        String result = renderer.renderByLayerKey("thymeleafRegist", model());

        assertThat(result).contains("<textarea class=\"krds-input medium egov-control egov-textarea\"");
        assertThat(result).contains("th:name=\"nttCn\"");
        assertThat(result).doesNotContain("id=\"nttCn\"\n                               th:name=\"nttCn\"");
    }

    @Test
    void thymeleafRegist_containsConditionalCsrf() {
        assertThat(renderer.renderByLayerKey("thymeleafRegist", model()))
                .contains("th:if=\"${_csrf != null}\"")
                .contains("th:name=\"${_csrf.parameterName}\"")
                .contains("th:value=\"${_csrf.token}\"");
    }

    @Test
    void thymeleafRegist_marksOnlyRequiredFields() {
        assertRequiredFieldMarks(renderer.renderByLayerKey("thymeleafRegist", model()));
    }

    // ─── thymeleafUpdt ───────────────────────────────────────────────────────

    @Test
    void thymeleafUpdt_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafUpdt", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
        assertThat(result).contains("layout:fragment=\"content\"");
        assertThat(result).contains("<h1 class=\"egov-page-title\">");
        assertThat(result).doesNotContain("style=\"");
    }

    @Test
    void thymeleafUpdt_rendersNttCnAsTextarea() {
        String result = renderer.renderByLayerKey("thymeleafUpdt", model());

        assertThat(result).contains("<textarea class=\"krds-input medium egov-control egov-textarea\"");
        assertThat(result).contains("th:name=\"nttCn\"");
        assertThat(result).doesNotContain("id=\"nttCn\"\n                               th:name=\"nttCn\"");
    }

    @Test
    void thymeleafUpdt_containsConditionalCsrf() {
        assertThat(renderer.renderByLayerKey("thymeleafUpdt", model()))
                .contains("th:if=\"${_csrf != null}\"")
                .contains("th:name=\"${_csrf.parameterName}\"")
                .contains("th:value=\"${_csrf.token}\"");
    }

    @Test
    void thymeleafUpdt_marksOnlyRequiredFields() {
        assertRequiredFieldMarks(renderer.renderByLayerKey("thymeleafUpdt", model()));
    }

    // ─── layoutMode=none — 독립 화면 템플릿 ─────────────────────────────────

    @Test
    void thymeleafList_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafList", model(), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .contains("BBS 목록")
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
    void thymeleafUpdt_none_rendersStandaloneWithoutLayoutOrBreadcrumb() {
        String result = renderer.renderByLayerKey(
                "thymeleafUpdt", model(), "layout/default", "layout/breadcrumb", "layout",
                CrudLayoutMode.NONE);

        assertThat(result)
                .contains("<meta charset=\"UTF-8\">")
                .doesNotContain("layout:decorate")
                .doesNotContain("breadcrumb")
                .doesNotContain("<#include");
    }

    // ─── JSP views ───────────────────────────────────────────────────────────

    @Test
    void jspViews_linkStylesCssAndKrdsScript() {
        List.of("jspList", "jspDetail", "jspRegist", "jspUpdt").forEach(layerKey -> {
            String result = renderer.renderByLayerKey(layerKey, model());

            assertThat(result).contains("/resources/css/styles.css");
            assertThat(result).contains("/resources/js/krds.min.js");
            assertThat(result).doesNotContain("krds.min.css");
            assertThat(result).doesNotContain("egov-layout.css");
        });
    }

    @Test
    void jspPostFormsContainConditionalCsrf() {
        List.of("jspDetail", "jspRegist", "jspUpdt").forEach(layerKey ->
                assertThat(renderer.renderByLayerKey(layerKey, model()))
                        .contains("${not empty _csrf}")
                        .contains("${_csrf.parameterName}")
                        .contains("${_csrf.token}"));
    }

    // ─── 알 수 없는 layerKey ─────────────────────────────────────────────────

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

    @Test
    void metadataModelRendersDisplayNameListAliasAndDefaultBbsId() {
        BoardTemplateModel base = model();
        BoardTemplateModel metadataModel = new BoardTemplateModel(
                base.packageName(), base.domain(), base.domainLc(), base.domainKr(), base.tableName(),
                base.masterTableName(), base.useTableName(), base.urlPrefix(), base.date(),
                base.egovVersion(), base.jakartaValidation(), base.bbsId(), base.nttId(),
                base.hasFile(), base.atchFileId(), base.fileDetailTableName(), base.fields(),
                base.listFields(), base.insertFields(), base.formFields(), base.searchFields(),
                base.noticeAtExists(),
                new BoardDisplayModel("EgovInfoNotice", "공지사항", "알림정보"),
                new BoardRouteModel(base.urlPrefix(),
                        "/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE",
                        "/cop/bbs/selectBoardList.do", "BBS_NOTICE"));

        String controller = renderer.renderByLayerKey("controller", metadataModel);
        String list = renderer.renderByLayerKey("thymeleafList", metadataModel);

        assertThat(controller)
                .contains("private static final String DEFAULT_BBS_ID = \"BBS_NOTICE\"")
                .contains("@GetMapping({\"/bbs/bbsList.do\", \"/cop/bbs/selectBoardList.do\"})")
                .contains("model.addAttribute(\"menuContextUrl\", \"/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE\")")
                .doesNotContain("@GetMapping({\"/bbs/bbsList.do\", \"/cop/bbs/selectBoardList.do?bbsId");
        assertThat(list).contains("<title>공지사항 목록</title>")
                .contains("<h1 class=\"egov-page-title\">공지사항 목록</h1>")
                .contains("<caption>공지사항 목록 표</caption>");
    }

    private void assertRequiredFieldMarks(String result) {
        assertThat(result)
                .contains("제목<span class=\"egov-required-mark\">*</span>")
                .doesNotContain("내용<span class=\"egov-required-mark\">*</span>");
    }
}
