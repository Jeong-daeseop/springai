package com.krdevops.springai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratedCodeContractAuditorTest {

    private final GeneratedCodeContractAuditor auditor = new GeneratedCodeContractAuditor();

    @Test
    void findsTemplateAndClaudeDesignMarkers(@TempDir Path root) throws Exception {
        Path templates = Files.createDirectories(root.resolve("templates"));
        Files.writeString(templates.resolve("list.html"),
                "<#if ready><div x-dc=\"1\">{{DOMAIN}}</div></#if>");

        assertThat(auditor.audit(root.toString()))
                .anyMatch(value -> value.contains("FreeMarker"))
                .anyMatch(value -> value.contains("{{...}}"))
                .anyMatch(value -> value.contains("x-dc"));
    }

    @Test
    void acceptsResolvedLocalView(@TempDir Path root) throws Exception {
        Path templates = Files.createDirectories(root.resolve("templates"));
        Files.writeString(templates.resolve("list.html"),
                "<table class=\"krds-table-wrap\"><tr><td th:text=\"${item.title}\"></td></tr></table>");

        assertThat(auditor.audit(root.toString())).isEmpty();
    }

    @Test
    void findsBasicAccessibilityContractViolations(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("view.html"),
                "<html><body><img src=\"local.png\"><button><span></span></button></body></html>");

        assertThat(auditor.auditAccessibility(root.toString()))
                .anyMatch(value -> value.contains("lang"))
                .anyMatch(value -> value.contains("alt"))
                .anyMatch(value -> value.contains("button"));
    }

    @Test
    void findsMissingKrdsSizeAndPostCsrfContracts(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("form.html"), """
                <html lang="ko"><body>
                <form method="post"><input class="krds-input" name="title"></form>
                <select class="krds-form-select"><option>선택</option></select>
                </body></html>
                """);

        assertThat(auditor.audit(root.toString()))
                .anyMatch(value -> value.contains("KRDS 크기 modifier 누락: krds-input"))
                .anyMatch(value -> value.contains("KRDS 크기 modifier 누락: krds-form-select"))
                .anyMatch(value -> value.contains("POST form CSRF 토큰 누락"));
    }

    @Test
    void acceptsSizedKrdsControlsAndCsrfProtectedPostForm(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("form.html"), """
                <html lang="ko"><body>
                <form method="post">
                  <input type="hidden" name="_csrf" value="token">
                  <input class="krds-input medium" name="title">
                  <button class="krds-btn primary medium" type="submit">저장</button>
                </form>
                </body></html>
                """);

        assertThat(auditor.audit(root.toString())).isEmpty();
    }

    @Test
    void findsMissingCrudScopedButtonClass(@TempDir Path root) throws Exception {
        Files.writeString(root.resolve("list.html"), """
                <html lang="ko"><body><main class="egov-crud-page">
                  <a class="krds-btn primary medium" href="/regist">등록</a>
                </main></body></html>
                """);

        assertThat(auditor.audit(root.toString()))
                .anyMatch(value -> value.contains("CRUD 공통 버튼 클래스 누락: egov-btn"));
    }
}
