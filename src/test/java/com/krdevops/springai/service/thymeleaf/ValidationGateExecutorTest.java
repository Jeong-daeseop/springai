package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
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
}
