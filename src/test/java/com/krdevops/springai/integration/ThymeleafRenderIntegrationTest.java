package com.krdevops.springai.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * I-7: 생성된 Thymeleaf HTML이 실제 Thymeleaf 엔진에서 올바르게 렌더링되는지 검증.
 * Thymeleaf TemplateEngine을 활용하여 다양한 템플릿 시나리오 테스트.
 */
class ThymeleafRenderIntegrationTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        StringTemplateResolver resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        templateEngine = new org.thymeleaf.spring6.SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Test
    void themeleafEngineAvailable() {
        assertThat(templateEngine).isNotNull();
    }

    @Test
    void canRenderSimpleThymeleafTemplate() {
        String template = "<html th:lang=\"ko\">\n" +
                "<head><title th:text=\"${pageTitle}\">Default</title></head>\n" +
                "<body><h1 th:text=\"${pageTitle}\">Title</h1></body>\n" +
                "</html>";

        Context context = new Context(Locale.KOREA);
        context.setVariable("pageTitle", "테스트 페이지");

        assertThatCode(() -> templateEngine.process(template, context))
                .doesNotThrowAnyException();
    }

    @Test
    void canRenderListTableWithThymeleaf() {
        String template = "<table>\n" +
                "  <tr th:each=\"item : ${resultList}\">\n" +
                "    <td th:text=\"${item.name}\">Name</td>\n" +
                "    <td th:text=\"${item.id}\">ID</td>\n" +
                "  </tr>\n" +
                "</table>";

        Context context = new Context(Locale.KOREA);
        Map<String, String> item1 = new HashMap<>();
        item1.put("name", "직원1");
        item1.put("id", "001");

        Map<String, String> item2 = new HashMap<>();
        item2.put("name", "직원2");
        item2.put("id", "002");

        context.setVariable("resultList", List.of(item1, item2));

        assertThatCode(() -> {
            String result = templateEngine.process(template, context);
            assertThat(result).contains("직원1").contains("직원2");
        }).doesNotThrowAnyException();
    }

    @Test
    void canRenderFormWithThymeleafFieldBinding() {
        String template = "<form method=\"post\">\n" +
                "  <input type=\"text\" name=\"employerName\" th:value=\"${employerVO.employerName}\" />\n" +
                "  <input type=\"text\" name=\"email\" th:value=\"${employerVO.email}\" />\n" +
                "</form>";

        Context context = new Context(Locale.KOREA);
        Map<String, String> vo = new HashMap<>();
        vo.put("employerName", "김철수");
        vo.put("email", "kim@example.com");
        context.setVariable("employerVO", vo);

        assertThatCode(() -> {
            String result = templateEngine.process(template, context);
            assertThat(result).contains("김철수").contains("kim@example.com");
        }).doesNotThrowAnyException();
    }

    @Test
    void canRenderConditionalThymeleafContent() {
        String template = "<div th:if=\"${#lists.isEmpty(resultList)}\">\n" +
                "  <p>조회된 데이터가 없습니다.</p>\n" +
                "</div>\n" +
                "<div th:unless=\"${#lists.isEmpty(resultList)}\">\n" +
                "  <p th:text=\"'총 ' + ${resultList.size()} + '개 항목'\">\n" +
                "</div>";

        Context emptyContext = new Context(Locale.KOREA);
        emptyContext.setVariable("resultList", List.of());

        Context nonEmptyContext = new Context(Locale.KOREA);
        nonEmptyContext.setVariable("resultList", List.of("item1", "item2"));

        assertThatCode(() -> {
            String emptyResult = templateEngine.process(template, emptyContext);
            assertThat(emptyResult).contains("조회된 데이터가 없습니다");

            String nonEmptyResult = templateEngine.process(template, nonEmptyContext);
            assertThat(nonEmptyResult).contains("총");
        }).doesNotThrowAnyException();
    }

    @Test
    void canRenderLayoutDecoratorWithThymeleaf() {
        String layoutTemplate = "<!DOCTYPE html>\n" +
                "<html xmlns:layout=\"http://www.ultraq.net.nz/thymeleaf/layout\">\n" +
                "<head><title>Base Layout</title></head>\n" +
                "<body>\n" +
                "  <header>Header</header>\n" +
                "  <div layout:fragment=\"content\">Content will go here</div>\n" +
                "  <footer>Footer</footer>\n" +
                "</body>\n" +
                "</html>";

        String contentTemplate = "<html xmlns:layout=\"http://www.ultraq.net.nz/thymeleaf/layout\"\n" +
                "      layout:decorate=\"~{this}\">\n" +
                "<div layout:fragment=\"content\">\n" +
                "  <h1 th:text=\"${pageTitle}\">Page Title</h1>\n" +
                "</div>\n" +
                "</html>";

        Context context = new Context(Locale.KOREA);
        context.setVariable("pageTitle", "테스트 페이지");

        assertThatCode(() -> templateEngine.process(contentTemplate, context))
                .doesNotThrowAnyException();
    }

    @Test
    void canHandleResponsiveDataAttributes() {
        String template = "<div data-egov-responsive=\"table-to-card\"\n" +
                "     data-egov-breakpoint-tablet=\"768\"\n" +
                "     data-egov-breakpoint-mobile=\"390\">\n" +
                "  <table th:each=\"item : ${resultList}\">\n" +
                "    <tr><td th:text=\"${item}\"></td></tr>\n" +
                "  </table>\n" +
                "</div>";

        Context context = new Context(Locale.KOREA);
        context.setVariable("resultList", List.of("데이터1", "데이터2"));

        assertThatCode(() -> {
            String result = templateEngine.process(template, context);
            assertThat(result).contains("data-egov-responsive")
                    .contains("data-egov-breakpoint-tablet=\"768\"")
                    .contains("data-egov-breakpoint-mobile=\"390\"");
        }).doesNotThrowAnyException();
    }

    @Test
    void canValidateFormErrorHandling() {
        String template = "<div class=\"form-group\">\n" +
                "  <label for=\"employerName\">직원명:</label>\n" +
                "  <input type=\"text\" id=\"employerName\" name=\"employerName\" " +
                "th:value=\"${employerVO.employerName}\" />\n" +
                "  <span th:if=\"${error != null}\" class=\"error\" th:text=\"${error}\">Error</span>\n" +
                "</div>";

        Context context = new Context(Locale.KOREA);
        context.setVariable("employerVO", Map.of("employerName", "테스트"));
        context.setVariable("error", "필수 입력 항목입니다");

        assertThatCode(() -> {
            String result = templateEngine.process(template, context);
            assertThat(result).contains("필수 입력 항목입니다");
        }).doesNotThrowAnyException();
    }

    @Test
    void canRenderPaginationInfo() {
        String template = "<nav th:if=\"${paginationInfo != null}\">\n" +
                "  <span th:text=\"'Page ' + ${paginationInfo.currentPageNo} + ' of ' + ${paginationInfo.totalPageCount}\"></span>\n" +
                "</nav>";

        Context context = new Context(Locale.KOREA);
        Map<String, Object> paginationInfo = new HashMap<>();
        paginationInfo.put("currentPageNo", 1);
        paginationInfo.put("totalPageCount", 10);
        context.setVariable("paginationInfo", paginationInfo);

        assertThatCode(() -> {
            String result = templateEngine.process(template, context);
            assertThat(result).contains("Page 1 of 10");
        }).doesNotThrowAnyException();
    }
}
