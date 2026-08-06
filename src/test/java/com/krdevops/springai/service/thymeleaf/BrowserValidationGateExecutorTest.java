package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.BrowserGateStatus;
import com.krdevops.springai.model.thymeleaf.BrowserValidationRequest;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserValidationGateExecutorTest {

    @TempDir
    Path tempDirectory;

    @Test
    void parsesRunnerEvidenceAndAggregatesThreeGateResults() throws Exception {
        Path runner = tempDirectory.resolve("fake-browser-gate.mjs");
        Files.writeString(runner, """
                process.stdout.write(JSON.stringify({
                  schemaVersion: 'browser-validation-report-v1', screenId: 'sample', blocked: true,
                  createdAt: '2026-08-06T00:00:00Z', browser: {engine:'chromium',version:'149'},
                  reportPath: '/tmp/report.json',
                  viewports: [{name:'mobile',width:390,height:844,
                    renderStatus:'PASSED',accessibilityStatus:'FAILED',visualStatus:'BASELINE_MISSING',
                    renderIssues:[],accessibilityIssues:['button-name(critical): button'],
                    violations:[{id:'button-name',impact:'critical',description:'name',helpUrl:'https://example.test',tags:['wcag2a'],targets:['button']}],
                    screenshotPath:'/tmp/current.png',baselinePath:'/tmp/baseline.png',diffPath:null,
                    differenceRatio:null,visualIssues:['기준 이미지가 없습니다.'],durationMs:12}]}) + '\\n');
                """);
        var executor = new BrowserValidationGateExecutor(
                new ObjectMapper().findAndRegisterModules(), "node", runner, Duration.ofSeconds(5));

        var report = executor.validate(BrowserValidationRequest.forHtml(
                "sample", "<!doctype html><html><title>x</title></html>",
                tempDirectory.resolve("artifacts"), tempDirectory.resolve("baselines")));

        assertTrue(report.blocked());
        assertEquals(BrowserGateStatus.FAILED, report.viewports().get(0).accessibilityStatus());
        assertEquals(3, report.toGateResults().size());
        assertTrue(report.toGateResults().stream()
                .filter(result -> result.gateType() == ValidationGateType.BROWSER_RENDER)
                .allMatch(result -> result.passed()));
        assertTrue(report.toGateResults().stream()
                .filter(result -> result.gateType() == ValidationGateType.ACCESSIBILITY)
                .noneMatch(result -> result.passed()));
        assertTrue(report.toGateResults().stream()
                .filter(result -> result.gateType() == ValidationGateType.VISUAL_PARITY)
                .noneMatch(result -> result.passed()));
    }

    @Test
    void missingRunnerReturnsInfrastructureEvidenceInsteadOfThrowing() {
        var executor = new BrowserValidationGateExecutor(
                new ObjectMapper().findAndRegisterModules(), "node", tempDirectory.resolve("missing.mjs"),
                Duration.ofSeconds(5));

        var report = executor.validate(BrowserValidationRequest.forHtml(
                "sample", "<!doctype html><html><title>x</title></html>",
                tempDirectory.resolve("artifacts"), tempDirectory.resolve("baselines")));

        assertTrue(report.blocked());
        assertEquals(3, report.viewports().size());
        assertTrue(report.viewports().stream()
                .allMatch(viewport -> viewport.renderStatus() == BrowserGateStatus.INFRA_ERROR));
        assertFalse(report.toGateResults().get(0).passed());
    }

    @Test
    void requestRequiresExactlyOneRenderSource() {
        assertThrows(IllegalArgumentException.class, () -> new BrowserValidationRequest(
                "sample", "http://localhost:8080", "<html></html>", tempDirectory, null,
                null, null, 0.001, 30_000));
    }
}
