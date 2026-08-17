package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** R6-T19: 생성 HTML을 실제 Thymeleaf 엔진으로 파싱·렌더하는 선택형 품질 게이트 검증. */
class ThymeleafRenderValidatorTest {

    private final ThymeleafRenderValidator validator = new ThymeleafRenderValidator();

    @Test
    void reportsMissingDirectoryWithoutScanningFiles() {
        var report = validator.validateDirectory("/no/such/directory/should-exist");

        assertThat(report.totalFiles()).isZero();
        assertThat(report.renderedFiles()).isZero();
        assertThat(report.failures()).anyMatch(failure -> failure.contains("디렉터리가 없습니다"));
        assertThat(report.passed()).isFalse();
    }

    @Test
    void emptyDirectoryPassesTrivially(@TempDir Path root) {
        var report = validator.validateDirectory(root.toString());

        assertThat(report.totalFiles()).isZero();
        assertThat(report.renderedFiles()).isZero();
        assertThat(report.failures()).isEmpty();
        assertThat(report.passed()).isTrue();
    }

    @Test
    void validThymeleafHtmlUsingFixtureVariablesRendersSuccessfully(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("list.html"), """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body>
                  <table>
                    <tr th:each="item : ${resultList}">
                      <td th:text="${item}">placeholder</td>
                    </tr>
                  </table>
                  <div th:if="${error}" th:text="${message}">error</div>
                  <form th:object="${searchVO}">
                    <span th:text="${paginationInfo}">page</span>
                  </form>
                </body>
                </html>
                """);

        var report = validator.validateDirectory(root.toString());

        assertThat(report.totalFiles()).isEqualTo(1);
        assertThat(report.renderedFiles()).isEqualTo(1);
        assertThat(report.failures()).isEmpty();
        assertThat(report.passed()).isTrue();
    }

    @Test
    void malformedThymeleafExpressionIsReportedAsFailureWithFileAndCause(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("broken.html"), """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body>
                  <span th:text="${unterminated string literal}">broken</span>
                </body>
                </html>
                """);

        var report = validator.validateDirectory(root.toString());

        assertThat(report.totalFiles()).isEqualTo(1);
        assertThat(report.renderedFiles()).isZero();
        assertThat(report.failures()).singleElement().satisfies(failure -> {
            assertThat(failure).contains("broken.html");
        });
        assertThat(report.passed()).isFalse();
    }

    @Test
    void reportsEachFailingFileSeparatelyWhileStillRenderingValidOnes(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("ok.html"), """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body><span th:text="${message}">ok</span></body>
                </html>
                """);
        Files.writeString(root.resolve("broken.html"), """
                <!DOCTYPE html>
                <html xmlns:th="http://www.thymeleaf.org">
                <body><span th:text="${unterminated string literal}">broken</span></body>
                </html>
                """);

        var report = validator.validateDirectory(root.toString());

        assertThat(report.totalFiles()).isEqualTo(2);
        assertThat(report.renderedFiles()).isEqualTo(1);
        assertThat(report.failures()).hasSize(1);
        assertThat(report.passed()).isFalse();
    }

    @Test
    void nonHtmlFilesAreIgnored(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("notes.txt"), "이건 HTML이 아니다 ${broken");

        var report = validator.validateDirectory(root.toString());

        assertThat(report.totalFiles()).isZero();
        assertThat(report.passed()).isTrue();
    }
}
