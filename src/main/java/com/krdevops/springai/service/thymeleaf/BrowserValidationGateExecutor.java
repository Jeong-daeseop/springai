package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.thymeleaf.BrowserGateStatus;
import com.krdevops.springai.model.thymeleaf.BrowserValidationReport;
import com.krdevops.springai.model.thymeleaf.BrowserValidationRequest;
import com.krdevops.springai.model.thymeleaf.BrowserViewportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * WP8 ARCH-0808~0810: Java 애플리케이션과 Playwright/axe/visual Node runner 사이의 Adapter.
 * 브라우저 프로세스 실패도 예외로 유실하지 않고 {@code INFRA_ERROR} report로 반환한다.
 */
@Service
public class BrowserValidationGateExecutor {

    private static final long MAX_RUNNER_OUTPUT_BYTES = 4L * 1024 * 1024;
    private static final List<Viewport> VIEWPORTS = List.of(
            new Viewport("desktop", 1440, 1200),
            new Viewport("tablet", 768, 1024),
            new Viewport("mobile", 390, 844));

    private final ObjectMapper objectMapper;
    private final String nodeBinary;
    private final Path runnerPath;
    private final Duration processTimeout;
    private OperationalTelemetry telemetry;

    @Autowired
    public BrowserValidationGateExecutor(ObjectMapper objectMapper) {
        this(objectMapper,
                System.getenv().getOrDefault("WP8_NODE_BINARY", "node"),
                Path.of(System.getenv().getOrDefault("WP8_BROWSER_GATE_RUNNER",
                        "jsp-design-extractor/scripts/browser-gate.mjs")),
                Duration.ofSeconds(Long.parseLong(
                        System.getenv().getOrDefault("WP8_BROWSER_GATE_TIMEOUT_SECONDS", "120"))));
    }

    BrowserValidationGateExecutor(
            ObjectMapper objectMapper, String nodeBinary, Path runnerPath, Duration processTimeout) {
        this.objectMapper = objectMapper;
        this.nodeBinary = nodeBinary;
        this.runnerPath = runnerPath.toAbsolutePath().normalize();
        this.processTimeout = processTimeout;
    }

    @Autowired
    void configureTelemetry(OperationalTelemetry telemetry) { this.telemetry = telemetry; }

    public BrowserValidationReport validate(BrowserValidationRequest request) {
        long startedAt = System.nanoTime();
        BrowserValidationReport report = validateInternal(request);
        record(report, Duration.ofNanos(System.nanoTime() - startedAt));
        return report;
    }

    private BrowserValidationReport validateInternal(BrowserValidationRequest request) {
        Path requestFile = null;
        try {
            if (!Files.isRegularFile(runnerPath)) {
                return infrastructureFailure(request.screenId(), "브라우저 Gate runner가 없습니다: " + runnerPath);
            }
            Files.createDirectories(request.artifactDirectory());
            requestFile = Files.createTempFile("wp8-browser-gate-", ".json");
            objectMapper.writeValue(requestFile.toFile(), runnerRequest(request));

            Process process = new ProcessBuilder(nodeBinary, runnerPath.toString(),
                    "--request", requestFile.toString())
                    .redirectErrorStream(true)
                    .start();
            CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> readOutput(process));
            boolean finished = process.waitFor(processTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                outputFuture.cancel(true);
                return infrastructureFailure(request.screenId(),
                        "브라우저 Gate timeout: " + processTimeout.toSeconds() + "초");
            }
            byte[] output = outputFuture.get(5, TimeUnit.SECONDS);
            if (output.length > MAX_RUNNER_OUTPUT_BYTES) {
                return infrastructureFailure(request.screenId(), "브라우저 Gate 출력이 4MB 제한을 초과했습니다.");
            }
            String text = new String(output, StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) {
                return infrastructureFailure(request.screenId(), "브라우저 Gate 실행 실패: " + abbreviate(text));
            }
            return objectMapper.readValue(text, BrowserValidationReport.class);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return infrastructureFailure(request.screenId(), "브라우저 Gate 실행이 중단되었습니다.");
        } catch (Exception exception) {
            return infrastructureFailure(request.screenId(), "브라우저 Gate 인프라 오류: " + exception.getMessage());
        } finally {
            if (requestFile != null) {
                try {
                    Files.deleteIfExists(requestFile);
                } catch (IOException ignored) {
                    // 임시 요청 파일 삭제 실패는 원래 Gate 결과를 바꾸지 않는다.
                }
            }
        }
    }

    private void record(BrowserValidationReport report, Duration totalDuration) {
        if (telemetry == null) return;
        long viewportCount = Math.max(1, report.viewports().size());
        Duration perViewport = totalDuration.dividedBy(viewportCount);
        for (BrowserViewportResult viewport : report.viewports()) {
            telemetry.gate(ValidationGateType.BROWSER_RENDER, GateSeverity.BLOCK,
                    viewport.renderStatus() == BrowserGateStatus.PASSED, perViewport);
            telemetry.gate(ValidationGateType.ACCESSIBILITY, GateSeverity.BLOCK,
                    viewport.accessibilityStatus() == BrowserGateStatus.PASSED, perViewport);
            telemetry.gate(ValidationGateType.VISUAL_PARITY, GateSeverity.BLOCK,
                    viewport.visualStatus() == BrowserGateStatus.PASSED, perViewport);
        }
    }

    private Map<String, Object> runnerRequest(BrowserValidationRequest request) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("screenId", request.screenId());
        if (request.url() != null && !request.url().isBlank()) value.put("url", request.url());
        if (request.renderedHtml() != null && !request.renderedHtml().isBlank()) {
            value.put("renderedHtml", request.renderedHtml());
        }
        value.put("artifactDirectory", request.artifactDirectory().toAbsolutePath().normalize().toString());
        if (request.baselineDirectory() != null) {
            value.put("baselineDirectory", request.baselineDirectory().toAbsolutePath().normalize().toString());
        }
        value.put("maskSelectors", request.maskSelectors());
        if (request.readySelector() != null && !request.readySelector().isBlank()) {
            value.put("readySelector", request.readySelector());
        }
        value.put("maxDifferenceRatio", request.maxDifferenceRatio());
        value.put("timeoutMillis", request.timeoutMillis());
        return value;
    }

    private BrowserValidationReport infrastructureFailure(String screenId, String issue) {
        List<BrowserViewportResult> results = VIEWPORTS.stream()
                .map(viewport -> new BrowserViewportResult(
                        viewport.name(), viewport.width(), viewport.height(),
                        BrowserGateStatus.INFRA_ERROR, BrowserGateStatus.INFRA_ERROR,
                        BrowserGateStatus.INFRA_ERROR, List.of(issue),
                        List.of("렌더 인프라 오류로 axe를 실행하지 못했습니다."), List.of(),
                        null, null, null, null,
                        List.of("렌더 인프라 오류로 시각 비교를 실행하지 못했습니다."), 0))
                .toList();
        return new BrowserValidationReport("browser-validation-report-v1", screenId, true,
                Instant.now(), null, null, results);
    }

    private byte[] readOutput(Process process) {
        try (InputStream input = process.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8_192];
            int read;
            long received = 0;
            while ((read = input.read(buffer)) != -1) {
                if (received <= MAX_RUNNER_OUTPUT_BYTES) {
                    int remaining = (int) Math.min(read, MAX_RUNNER_OUTPUT_BYTES + 1 - received);
                    if (remaining > 0) output.write(buffer, 0, remaining);
                }
                received += read;
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String abbreviate(String value) {
        if (value == null || value.length() <= 2_000) return value;
        return value.substring(0, 2_000) + "...";
    }

    private record Viewport(String name, int width, int height) { }
}
