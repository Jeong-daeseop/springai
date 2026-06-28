package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardTemplateModel;
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

    private BoardTemplateModel model() {
        return new BoardTemplateModel(
                "egovframework.let.bbs",
                "Bbs",
                "bbs",
                "BBS",
                "COMTNBBS",
                "COMTNBBSMASTER",
                "COMTNBBSUSE",
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

    // ─── layoutHtml ───────────────────────────────────────────────────────────

    @Test
    void layoutHtml_rendersWithoutFreeMarkerSyntax() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).doesNotContain("<#");
        assertThat(result).contains("${#authentication?.name ?: '관리자'}");
        assertThat(result).contains("${#httpServletRequest != null and #httpServletRequest.requestURI != null and #httpServletRequest.requestURI.contains('/cop/emp/')}");
    }

    @Test
    void layoutHtml_containsContentFragment() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).contains("layout:fragment=\"content\"");
    }

    @Test
    void layoutHtml_containsBootstrapCdn() {
        String result = renderer.renderByLayerKey("layoutHtml", model());

        assertThat(result).contains("bootstrap");
    }

    // ─── mapper ───────────────────────────────────────────────────────────────

    @Test
    void mapper_containsMapperAnnotation() {
        String result = renderer.renderByLayerKey("mapper", model());

        assertThat(result).contains("import org.apache.ibatis.annotations.Mapper;");
        assertThat(result).contains("@Mapper");
    }

    // ─── vo ───────────────────────────────────────────────────────────────────

    @Test
    void vo_sizeMax_usesPlainNumberWithoutThousandsSeparator() {
        String result = renderer.renderByLayerKey("vo", model());

        assertThat(result).contains("@Size(max = 2000)");
        assertThat(result).doesNotContain("@Size(max = 2,000)");
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
    }

    // ─── thymeleafDetail ─────────────────────────────────────────────────────

    @Test
    void thymeleafDetail_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafDetail", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
        assertThat(result).contains("layout:fragment=\"content\"");
    }

    // ─── thymeleafRegist ─────────────────────────────────────────────────────

    @Test
    void thymeleafRegist_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafRegist", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
        assertThat(result).contains("layout:fragment=\"content\"");
    }

    // ─── thymeleafUpdt ───────────────────────────────────────────────────────

    @Test
    void thymeleafUpdt_containsLayoutDecorate() {
        String result = renderer.renderByLayerKey("thymeleafUpdt", model());

        assertThat(result).contains("layout:decorate=\"~{layout/default}\"");
        assertThat(result).contains("layout:fragment=\"content\"");
    }

    // ─── 알 수 없는 layerKey ─────────────────────────────────────────────────

    @Test
    void renderByLayerKey_unknownKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> renderer.renderByLayerKey("unknown", model()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }
}
