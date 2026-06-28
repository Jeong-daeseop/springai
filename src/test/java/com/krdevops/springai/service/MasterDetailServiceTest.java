package com.krdevops.springai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MasterDetailServiceTest {

    JdbcTemplate jdbcTemplate;
    MasterDetailService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new MasterDetailService(
                jdbcTemplate,
                mock(TableRelationService.class),
                new EgovPromptBuilder());

        when(jdbcTemplate.queryForList(anyString(), eq("com"), eq("COMTNBBSMASTER")))
                .thenReturn(List.of(
                        column("BBS_ID", "varchar", 20, "NO", "게시판ID", "PRI"),
                        column("BBS_NM", "varchar", 255, "NO", "게시판명", "")));
        when(jdbcTemplate.queryForList(anyString(), eq("com"), eq("COMTNBBSUSE")))
                .thenReturn(List.of(
                        column("BBS_ID", "varchar", 20, "NO", "게시판ID", "PRI"),
                        column("TRGET_ID", "varchar", 20, "NO", "대상ID", "")));
    }

    @Test
    void buildMasterDetailPrompt_defaultViewTypeKeepsJspOutput() {
        String result = service.buildMasterDetailPrompt(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE",
                "BbsMaster", "egovframework.let.bbs", "/tmp/egov-web");

        assertThat(result)
                .contains("viewType          = jsp")
                .contains("[생성 파일 목록 — 13개 파일]")
                .contains("EgovBbsMasterList.jsp")
                .contains("EgovBbsMasterDetail.jsp")
                .contains("<c:forEach items=\"${detailList}\" var=\"detail\">")
                .contains("✅ 13개 파일 생성 완료")
                .doesNotContain("layout/default.html")
                .doesNotContain("EgovBbsMasterList.html");
    }

    @Test
    void buildMasterDetailPrompt_thymeleafViewTypeReturnsLayoutAndHtmlInstructions() {
        String result = service.buildMasterDetailPrompt(
                "com", "COMTNBBSMASTER", "COMTNBBSUSE",
                "BbsMaster", "egovframework.let.bbs", "/tmp/egov-web", "thymeleaf");

        assertThat(result)
                .contains("viewType          = thymeleaf")
                .contains("src/main/resources/templates/bbsMaster/EgovBbsMaster*.html")
                .contains("[생성 파일 목록 — 14개 파일]")
                .contains("layout/default.html")
                .contains("EgovBbsMasterList.html")
                .contains("EgovBbsMasterDetail.html")
                .contains("layout:decorate=\"~{layout/default}\"")
                .contains("<tr th:each=\"detail : ${detailList}\">")
                .contains("JSP taglib, <c:forEach>, <c:out>, form 태그는 사용하지 마세요.")
                .contains("✅ 14개 파일 생성 완료")
                .doesNotContain("EgovBbsMasterList.jsp")
                .doesNotContain("EgovBbsMasterDetail.jsp");
    }

    private Map<String, Object> column(String name, String type, Integer maxLength,
                                       String nullable, String comment, String key) {
        return Map.of(
                "COLUMN_NAME", name,
                "DATA_TYPE", type,
                "CHARACTER_MAXIMUM_LENGTH", maxLength,
                "IS_NULLABLE", nullable,
                "COLUMN_COMMENT", comment,
                "COLUMN_KEY", key);
    }
}
