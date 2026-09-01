package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class CodeValidatorServiceTest {

    private final CodeValidatorService validator = new CodeValidatorService();

    @Test
    void passesWellFormedThymeleafScreenFile(@TempDir Path root) throws Exception {
        Path file = Files.writeString(root.resolve("EmployerList.html"), """
                <!DOCTYPE html>
                <html lang="ko" xmlns:th="http://www.thymeleaf.org"
                      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
                      layout:decorate="~{layout/default}">
                <section layout:fragment="content">
                    <input type="hidden" name="pageIndex" th:value="${searchVO.pageIndex}"/>
                    <a th:href="@{/emp/employerDetail.do}" th:text="${item.name}"></a>
                </section>
                </html>
                """);

        String result = validator.validateFile(file.toString());

        assertThat(result).doesNotContain("❌");
        assertThat(result).contains("✅ Thymeleaf 네임스페이스 선언");
        assertThat(result).contains("✅ th:* 속성 사용");
        assertThat(result).contains("✅ 공통 layout 적용(layout:decorate)");
        assertThat(result).contains("✅ 페이지 인덱스 처리");
    }

    @Test
    void flagsThymeleafScreenFileMissingLayoutDecorateAndThAttributes(@TempDir Path root) throws Exception {
        Path file = Files.writeString(root.resolve("EmployerDetail.html"), """
                <!DOCTYPE html>
                <html lang="ko">
                <section>상세 화면 본문</section>
                </html>
                """);

        String result = validator.validateFile(file.toString());

        assertThat(result).contains("❌ Thymeleaf 네임스페이스 선언");
        assertThat(result).contains("❌ th:* 속성 사용");
        assertThat(result).contains("❌ 공통 layout 적용(layout:decorate)");
        assertThat(result).doesNotContain("페이지 인덱스 처리");
    }

    @Test
    void passesLayoutFragmentFileWithoutRequiringLayoutDecorate(@TempDir Path root) throws Exception {
        Path file = Files.writeString(root.resolve("gnb.html"), """
                <th:block th:fragment="gnb" xmlns:th="http://www.thymeleaf.org">
                <nav th:each="menu : ${gnbMenus}" th:text="${menu.menuNm}"></nav>
                </th:block>
                """);

        String result = validator.validateFile(file.toString());

        assertThat(result).doesNotContain("❌");
        assertThat(result).contains("✅ layout 조각 정의(th:fragment/layout:fragment)");
        assertThat(result).doesNotContain("layout:decorate");
    }

    @Test
    void validateDirectoryNowIncludesHtmlFiles(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("List.html"), "<html><body>no th namespace</body></html>");

        String result = validator.validateDirectory(root.toString());

        assertThat(result).contains("파일: 1개");
        assertThat(result).contains("List.html");
    }

    @Test
    void jspValidationIsUnaffectedByThymeleafRules(@TempDir Path root) throws Exception {
        Path file = Files.writeString(root.resolve("EmployerList.jsp"), """
                <%@ page contentType="text/html; charset=UTF-8" %>
                <%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
                <c:url value="/emp/employerList.do" var="listUrl"/>
                <input type="hidden" name="pageIndex" value="${searchVO.pageIndex}"/>
                """);

        String result = validator.validateFile(file.toString());

        assertThat(result).doesNotContain("❌");
        assertThat(result).contains("✅ UTF-8 인코딩 선언");
        assertThat(result).contains("✅ JSTL core 태그 선언");
        assertThat(result).contains("✅ <c:url> URL 처리");
    }
}
