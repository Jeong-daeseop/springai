package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ThymeleafRenderValidatorTest {

    private final ThymeleafRenderValidator validator = new ThymeleafRenderValidator();

    @Test
    void rendersValidTemplateWithFixtureContext(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("list.html"), """
                <!doctype html><html lang="ko" xmlns:th="http://www.thymeleaf.org">
                <body><p th:text="${message}">message</p>
                <li th:each="item : ${resultList}" th:text="${item}"></li></body></html>
                """);

        ThymeleafRenderValidator.RenderReport report = validator.validateDirectory(root.toString());

        assertThat(report.passed()).isTrue();
        assertThat(report.totalFiles()).isEqualTo(1);
        assertThat(report.renderedFiles()).isEqualTo(1);
    }

    @Test
    void reportsInvalidThymeleafExpression(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("broken.html"),
                "<html><body><p th:text=\"${\">x</p></body></html>");

        ThymeleafRenderValidator.RenderReport report = validator.validateDirectory(root.toString());

        assertThat(report.passed()).isFalse();
        assertThat(report.failures()).anyMatch(message -> message.contains("broken.html"));
    }
}
