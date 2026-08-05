package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidationGateExecutorTest {

    private ValidationGateExecutor executor;
    private Path tempPath;

    @BeforeEach
    void setUp() throws Exception {
        executor = new ValidationGateExecutor();
        tempPath = Files.createTempFile("test", ".html");
    }

    @Test
    void testValidateThymeleafParseWellFormed() {
        String html = "<div><form th:action=\"/submit\"><input type=\"text\" /></form></div>";
        ValidationGateResult result = executor.validateThymeleafParse(html);

        assertTrue(result.passed(), "Well-formed HTML should pass");
        assertEquals(ValidationGateType.THYMELEAF_PARSE, result.gateType());
    }

    @Test
    void testValidateThymeleafParseMalformed() {
        String html = "<div><form><input type=\"text\"></div>";
        ValidationGateResult result = executor.validateThymeleafParse(html);

        assertFalse(result.passed(), "Malformed HTML should fail");
        assertTrue(result.hasIssues(), "Should have issues");
    }

    @Test
    void testValidateBindingContractPass() {
        String html = "<input th:field=\"*{username}\" /> <input th:field=\"*{email}\" />";
        Set<String> expected = Set.of("th:field=\"*{username}\"", "th:field=\"*{email}\"");

        ValidationGateResult result = executor.validateBindingContract(html, expected);

        assertTrue(result.passed(), "All bindings should be found");
    }

    @Test
    void testValidateBindingContractMissing() {
        String html = "<input th:field=\"*{username}\" />";
        Set<String> expected = Set.of("th:field=\"*{username}\"", "th:field=\"*{email}\"");

        ValidationGateResult result = executor.validateBindingContract(html, expected);

        assertFalse(result.passed(), "Missing binding should fail");
        assertTrue(result.hasIssues(), "Should have issues");
        assertTrue(result.issues().size() > 0, "Should report missing binding");
    }

    @Test
    void testValidateRouteParityMatch() {
        String originalRoute = "/admin/users/save";
        String template = "<form th:action=\"/admin/users/save\" method=\"post\"></form>";

        ValidationGateResult result = executor.validateRouteParity(originalRoute, template);

        assertTrue(result.passed(), "Matching route should pass");
    }

    @Test
    void testValidateRouteParityMatchesLinkExpressionSyntax() {
        // WP6 BindingComposer가 실제로 만드는 형태: th:action="@{route}" (Thymeleaf 링크 표현식).
        // 기존 검증은 "th:action=\"route\""(래핑 없는 형태)만 봐서 이 실제 형태를 놓치고 있었다.
        String originalRoute = "/emp/employerList.do";
        String template = "<form th:action=\"@{/emp/employerList.do}\" method=\"get\"></form>";

        ValidationGateResult result = executor.validateRouteParity(originalRoute, template);

        assertTrue(result.passed(), "issues: " + result.issues());
    }

    @Test
    void testValidateRouteParityMismatch() {
        String originalRoute = "/admin/users/save";
        String template = "<form th:action=\"/admin/users/update\" method=\"post\"></form>";

        ValidationGateResult result = executor.validateRouteParity(originalRoute, template);

        assertFalse(result.passed(), "Mismatched route should fail");
    }

    @Test
    void testValidateNoOverflowWithinLimit() {
        String html = "<div style=\"width: 1200px;\"></div>";

        ValidationGateResult result = executor.validateNoOverflow(html);

        assertTrue(result.passed(), "Width within limit should pass");
    }

    @Test
    void testValidateNoOverflowExceedsLimit() {
        String html = "<div style=\"width: 1600px;\"></div>";

        ValidationGateResult result = executor.validateNoOverflow(html);

        assertFalse(result.passed(), "Width exceeding desktop limit should fail");
    }

    @Test
    void testValidateBuild() throws Exception {
        String htmlContent = "<div>Test content</div>";

        ValidationGateResult result = executor.validateBuild(tempPath, htmlContent);

        assertTrue(result.passed(), "Valid build should pass");
        assertEquals(ValidationGateType.BUILD_VALIDATION, result.gateType());
    }

    @Test
    void testValidateBuildFileTooLarge() throws Exception {
        String htmlContent = "x".repeat(1_000_001);

        ValidationGateResult result = executor.validateBuild(tempPath, htmlContent);

        assertFalse(result.passed(), "File exceeding 1MB should fail");
        assertTrue(result.issues().stream().anyMatch(s -> s.contains("크기")), "Should report size issue");
    }

    // ── ARCH-0803/0804: 실제 Spring TemplateEngine 렌더 Gate ─────────────────────

    @Test
    void testValidateTemplateEngineRenderPassesForValidTemplate() {
        String fragment = "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\">"
                + "<div th:text=\"${title}\">placeholder</div></html>";

        ValidationGateResult result = executor.validateTemplateEngineRender(fragment, Map.of("title", "직원 목록"));

        assertTrue(result.passed(), "issues: " + result.issues());
        assertEquals(ValidationGateType.TEMPLATE_ENGINE_RENDER, result.gateType());
    }

    @Test
    void testValidateTemplateEngineRenderFailsForUnresolvedExpression() {
        String fragment = "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\">"
                + "<div th:text=\"${missingVariable.nestedField}\">placeholder</div></html>";

        ValidationGateResult result = executor.validateTemplateEngineRender(fragment, Map.of());

        assertFalse(result.passed(), "존재하지 않는 변수를 참조하면 실제 렌더에서 실패해야 한다");
        assertTrue(result.hasIssues());
    }

    // ── ARCH-0801: 공통 BLOCK/WARN 정책 ─────────────────────────────────────────

    @Test
    void testSeverityPolicyBlocksParseRenderBindingAndRoute() {
        assertEquals(GateSeverity.BLOCK, executor.severityOf(ValidationGateType.THYMELEAF_PARSE));
        assertEquals(GateSeverity.BLOCK, executor.severityOf(ValidationGateType.TEMPLATE_ENGINE_RENDER));
        assertEquals(GateSeverity.BLOCK, executor.severityOf(ValidationGateType.BINDING_VALIDATION));
        assertEquals(GateSeverity.BLOCK, executor.severityOf(ValidationGateType.ROUTE_PARITY));
    }

    @Test
    void testSeverityPolicyWarnsOverflow() {
        assertEquals(GateSeverity.WARN, executor.severityOf(ValidationGateType.OVERFLOW_CHECK));
    }

    /**
     * {@code @{...}}/{@code th:field}는 서블릿 요청 컨텍스트가 필요해 이 Gate의 비-web
     * {@link org.thymeleaf.context.Context}로는 항상 실패한다 — Javadoc에 문서화된 범위 제한을
     * 실제로 증명하는 회귀 테스트(우연히 통과하기 시작하면 이 테스트가 잡아야 문서와 코드가
     * 어긋나지 않는다).
     */
    @Test
    void testValidateTemplateEngineRenderCannotHandleLinkExpressionOrFieldBindingWithoutWebContext() {
        String fragment = "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\">"
                + "<form th:action=\"@{/emp/employerList.do}\"><input th:field=\"*{name}\"/></form></html>";

        ValidationGateResult result = executor.validateTemplateEngineRender(fragment, Map.of());

        assertFalse(result.passed(), "web context 없이는 @{...}/th:field가 실패해야 한다(알려진 범위 제한)");
    }

    // ── ARCH-0805/0811: Gate 묶음 실행과 Validation Report ──────────────────────

    @Test
    void testRunThymeleafGatesReportsNotBlockedWhenAllPass() {
        String html = "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\">"
                + "<form th:action=\"/emp/employerList.do\" method=\"get\">"
                + "<input th:field=\"*{name}\" /></form></html>";

        ValidationReport report = executor.runThymeleafGates(
                "emp-list", "/emp/employerList.do", html, Set.of("th:field=\"*{name}\""));

        assertFalse(report.blocked(), "issues: " + report.results());
        assertEquals("emp-list", report.screenId());
        assertTrue(report.results().stream().anyMatch(r -> r.gateType() == ValidationGateType.THYMELEAF_PARSE));
        assertTrue(report.results().stream().anyMatch(r -> r.gateType() == ValidationGateType.BINDING_VALIDATION));
        assertTrue(report.results().stream().anyMatch(r -> r.gateType() == ValidationGateType.ROUTE_PARITY));
        assertNotNull(report.inputHash());
    }

    @Test
    void testRunThymeleafGatesBlocksWhenRouteMismatches() {
        String html = "<form th:action=\"/emp/employerList.do\" method=\"get\"></form>";

        ValidationReport report = executor.runThymeleafGates(
                "emp-list", "/emp/employerWrongRoute.do", html, Set.of());

        assertTrue(report.blocked());
        assertTrue(report.results().stream()
                .anyMatch(r -> r.gateType() == ValidationGateType.ROUTE_PARITY && !r.passed()));
    }

    @Test
    void testRunThymeleafGatesDoesNotBlockOnOverflowWarningAlone() {
        String html = "<!DOCTYPE html><html xmlns:th=\"http://www.thymeleaf.org\">"
                + "<form th:action=\"/emp/employerList.do\" method=\"get\">"
                + "<div style=\"width: 1600px;\"></div></form></html>";

        ValidationReport report = executor.runThymeleafGates(
                "emp-list", "/emp/employerList.do", html, Set.of());

        assertTrue(report.results().stream()
                .anyMatch(r -> r.gateType() == ValidationGateType.OVERFLOW_CHECK && !r.passed()));
        assertFalse(report.blocked(), "OVERFLOW_CHECK는 WARN이라 단독으로는 report를 막지 않아야 한다");
    }
}
