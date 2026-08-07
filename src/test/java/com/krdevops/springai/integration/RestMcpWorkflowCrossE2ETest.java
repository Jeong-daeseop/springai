package com.krdevops.springai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.controller.ThymeleafOperationsController;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.artifact.FilesystemArtifactStore;
import com.krdevops.springai.service.artifact.RecordingArtifactCatalog;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.operation.NoopOperationEventPort;
import com.krdevops.springai.service.operation.NoopOperationLockPort;
import com.krdevops.springai.service.thymeleaf.BaselineApprovalService;
import com.krdevops.springai.service.thymeleaf.BrowserGateDirectoryResolver;
import com.krdevops.springai.service.thymeleaf.InMemoryThymeleafOperationStore;
import com.krdevops.springai.service.thymeleaf.ProjectOperationStateService;
import com.krdevops.springai.service.thymeleaf.ThymeleafOperationStore;
import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import com.krdevops.springai.service.thymeleaf.ThymeleafToolAuthorizationService;
import com.krdevops.springai.service.thymeleaf.TestBrowserGateExecutors;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import com.krdevops.springai.tools.ThymeleafProjectWorkflowTool;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** REST에서 만든 상태 원장을 MCP가 이어받아 승인·적용·검증하는 교차 계약 E2E. */
class RestMcpWorkflowCrossE2ETest {

    @TempDir Path projectRoot;

    @TempDir Path baselineRoot;
    @TempDir Path artifactStoreRoot;
    @TempDir Path runnerDirectory;

    private ObjectMapper objectMapper;
    private ThymeleafProjectWorkflowService workflow;
    private ThymeleafOperationsController rest;
    private ThymeleafProjectWorkflowTool mcp;

    private void wire() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(),
                new OperationHashFactory(objectMapper));
        rest = new ThymeleafOperationsController(workflow, null);
        mcp = new ThymeleafProjectWorkflowTool(
                new ThymeleafToolAuthorizationService("shared"), workflow, objectMapper);
    }

    /**
     * WP8 3차 pass: baseline 승인(REST)과 브라우저 Gate 재검증(MCP)이 같은 디렉터리 계약을 쓰는지
     * 확인하려면 두 진입점이 같은 store와 같은 {@link BrowserGateDirectoryResolver}를 공유해야 한다.
     */
    private void wireWithBrowserGate() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        OperationHashFactory hashFactory = new OperationHashFactory(objectMapper);
        SafePathResolver pathResolver = new SafePathResolver();
        ThymeleafOperationStore store = new InMemoryThymeleafOperationStore();
        var directories = new BrowserGateDirectoryResolver(pathResolver, baselineRoot);
        var browserGate = TestBrowserGateExecutors.withRunner(
                objectMapper, baselineAwareRunner(), Duration.ofSeconds(20));
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(artifactStoreRoot);
        var artifactService = new ArtifactService(
                new FilesystemArtifactStore(properties), new RecordingArtifactCatalog());

        workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(), hashFactory, null,
                store, new NoopOperationEventPort(), new NoopOperationLockPort(), artifactService,
                pathResolver, new FileSystemApprovedProjectWritePort(pathResolver, hashFactory),
                browserGate, directories);
        var baselineApproval = new BaselineApprovalService(
                store, browserGate, directories, hashFactory, objectMapper, artifactService);
        rest = new ThymeleafOperationsController(workflow, baselineApproval);
        mcp = new ThymeleafProjectWorkflowTool(
                new ThymeleafToolAuthorizationService("shared"), workflow, objectMapper);
    }

    /** 승인된 baseline 파일이 실제로 있을 때만 PASSED를 돌려주는 대역 — 경로 계약이 어긋나면 실패한다. */
    private Path baselineAwareRunner() {
        try {
            Path script = Files.createTempFile(runnerDirectory, "fake-browser-gate-", ".mjs");
            Files.writeString(script, """
                    import fs from 'node:fs';
                    import path from 'node:path';
                    const requestPath = process.argv[process.argv.indexOf('--request') + 1];
                    const request = JSON.parse(fs.readFileSync(requestPath, 'utf8'));
                    fs.mkdirSync(request.artifactDirectory, { recursive: true });
                    const viewports = [['desktop',1440,1200],['tablet',768,1024],['mobile',390,844]]
                      .map(([name, width, height]) => {
                        const screenshotPath = path.join(request.artifactDirectory, `${request.screenId}-${name}.png`);
                        fs.writeFileSync(screenshotPath, `fake-png-${request.screenId}-${name}`);
                        const baselinePath = path.join(request.baselineDirectory, `${request.screenId}-${name}.png`);
                        const approved = fs.existsSync(baselinePath);
                        return { name, width, height,
                          renderStatus: 'PASSED', accessibilityStatus: 'PASSED',
                          visualStatus: approved ? 'PASSED' : 'BASELINE_MISSING',
                          renderIssues: [], accessibilityIssues: [], violations: [],
                          screenshotPath, baselinePath, diffPath: null,
                          differenceRatio: approved ? 0 : null,
                          visualIssues: approved ? [] : ['기준 이미지가 없습니다.'], durationMs: 1 };
                      });
                    process.stdout.write(JSON.stringify({
                      schemaVersion: 'browser-validation-report-v1', screenId: request.screenId,
                      blocked: viewports.some(v => v.visualStatus !== 'PASSED'),
                      createdAt: '2026-08-07T00:00:00Z',
                      browser: { engine: 'fake', version: '0' }, reportPath: null, viewports }));
                    """);
            return script;
        } catch (Exception exception) {
            throw new IllegalStateException("대역 runner 준비 실패", exception);
        }
    }

    @Test
    void restPreviewCanBeApprovedAppliedAndValidatedThroughMcp() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/list.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main><p th:text=\"${title}\"></p></main>")));
        String operationId = preview.operation().operationId();
        assertThat(projectRoot.resolve(relative)).doesNotExist();

        mcp.approveThymeleafProject("shared", operationId, preview.previewHash());
        mcp.applyThymeleafProject("shared", operationId);
        mcp.revalidateThymeleafProject("shared", operationId);

        assertThat(Files.readString(projectRoot.resolve(relative))).contains("th:text");
        assertThat(rest.report(operationId).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.VALIDATED);
    }

    /**
     * WP8 3차 pass/ARCH-0810/0811: REST preview → MCP approve/apply → REST baseline 승인 →
     * MCP 브라우저 Gate 재검증까지 이어지면 VALIDATED가 된다.
     */
    @Test
    void restBaselineApprovalUnblocksMcpBrowserGateRevalidation() throws Exception {
        wireWithBrowserGate();
        String relative = "src/main/resources/templates/users/list.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main><p th:text=\"${title}\"></p></main>")));
        String operationId = preview.operation().operationId();
        mcp.approveThymeleafProject("shared", operationId, preview.previewHash());
        mcp.applyThymeleafProject("shared", operationId);

        var approval = rest.approveBaseline(operationId, new ThymeleafOperationsController.BaselineApprovalBody(
                "users-list", relative, null, "<html><body>list</body></html>", List.of(), List.of(), null));
        assertThat(approval.viewports()).hasSize(3);
        assertThat(approval.viewports()).allSatisfy(viewport ->
                assertThat(viewport.previousContentHash()).isNull());

        mcp.revalidateThymeleafProjectWithBrowserGate("shared", operationId, List.of(
                new ThymeleafProjectWorkflowService.BrowserScreenValidationRequest(
                        "users-list", relative, null, "<html><body>list</body></html>", List.of(), null)));

        assertThat(rest.report(operationId).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.VALIDATED);
    }

    /** ARCH-0810: baseline 승인 없이 브라우저 Gate를 돌리면 자동 승격 없이 FAILED로 막힌다. */
    @Test
    void browserGateRevalidationWithoutApprovedBaselineFails() throws Exception {
        wireWithBrowserGate();
        String relative = "src/main/resources/templates/users/blocked.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main>list</main>")));
        String operationId = preview.operation().operationId();
        mcp.approveThymeleafProject("shared", operationId, preview.previewHash());
        mcp.applyThymeleafProject("shared", operationId);

        mcp.revalidateThymeleafProjectWithBrowserGate("shared", operationId, List.of(
                new ThymeleafProjectWorkflowService.BrowserScreenValidationRequest(
                        "users-blocked", relative, null, "<html><body>list</body></html>", List.of(), null)));

        var report = rest.report(operationId).getBody();
        assertThat(report.operation().status()).isEqualTo(ProjectOperationStatus.FAILED);
        assertThat(report.operation().validationErrors())
                .anySatisfy(error -> assertThat(error).contains("기준 이미지가 없습니다."));
    }

    /** 바디 없이 호출하는 기존 REST revalidate 계약은 그대로 동작해야 한다. */
    @Test
    void restRevalidateWithoutBodyKeepsLegacyBehaviour() throws Exception {
        wireWithBrowserGate();
        String relative = "src/main/resources/templates/users/legacy.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main>list</main>")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());
        mcp.applyThymeleafProject("shared", preview.operation().operationId());

        var result = rest.revalidate(preview.operation().operationId(), null);

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.VALIDATED);
    }

    /** ARCH-0813: 반대 방향 — MCP에서 만든 Preview를 REST가 승인하고 다시 MCP가 적용한다. */
    @Test
    void mcpPreviewCanBeApprovedThroughRestAndAppliedThroughMcp() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/detail.html";

        String previewJson = mcp.previewThymeleafProject(
                "shared", projectRoot.toString(), Map.of(relative, "<main><p th:text=\"${name}\"></p></main>"));
        var preview = objectMapper.readValue(previewJson, ThymeleafProjectWorkflowService.WorkflowResult.class);
        String operationId = preview.operation().operationId();

        var approved = rest.approve(operationId,
                new ThymeleafOperationsController.ApprovalRequest(preview.previewHash()));
        assertThat(approved.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);

        mcp.applyThymeleafProject("shared", operationId);

        assertThat(Files.readString(projectRoot.resolve(relative))).contains("th:text");
        assertThat(rest.report(operationId).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.APPLIED);
    }

    /** ARCH-0815: REST로 승인한 뒤 대상 파일이 바뀌면(drift) MCP Apply가 CONFLICT로 막고 아무 것도 쓰지 않는다. */
    @Test
    void sourceDriftAfterRestApprovalBlocksMcpApplyThroughReport() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/regist.html";
        Files.createDirectories(projectRoot.resolve(relative).getParent());
        Files.writeString(projectRoot.resolve(relative), "<main>original</main>");

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main>generated</main>")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());
        Files.writeString(projectRoot.resolve(relative), "<main>user-changed-after-approval</main>");

        mcp.applyThymeleafProject("shared", preview.operation().operationId());

        assertThat(rest.report(preview.operation().operationId()).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(Files.readString(projectRoot.resolve(relative))).isEqualTo("<main>user-changed-after-approval</main>");
    }

    /** ARCH-0815: MCP Apply 두 건이 동시에 들어와도 하나만 성공하고 나머지는 락으로 막힌다. */
    @Test
    void concurrentMcpApplyOnlyOneSucceeds() throws Exception {
        wire();
        String relative = "src/main/resources/templates/users/concurrent.html";

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(relative, "<main>generated</main>")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());

        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger lockedCount = new AtomicInteger();
        try {
            List<Future<?>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    try {
                        go.await();
                        mcp.applyThymeleafProject("shared", preview.operation().operationId());
                        successCount.incrementAndGet();
                    } catch (IllegalStateException e) {
                        if (e.getMessage() != null && e.getMessage().contains("LOCKED")) {
                            lockedCount.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // 락 경합 외 사유(이미 APPLIED 등)로 실패해도 이 테스트의 관심사는 "성공은 1건뿐"이다.
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            go.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(rest.report(preview.operation().operationId()).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.APPLIED);
    }

    /** ARCH-0816: MCP Apply 도중 실패하면 rollback되고 REST report에 FAILED로 남는다. */
    @Test
    void mcpApplyFailureRollsBackAndReportsFailedThroughRest() throws Exception {
        wire();
        Path first = projectRoot.resolve("a.html");
        Files.writeString(first, "original");
        // "blocked"를 파일로 만들어 blocked/b.html의 부모 디렉터리 생성이 실패하게 한다.
        Files.writeString(projectRoot.resolve("blocked"), "parent-is-a-file");

        var preview = rest.preview(new ThymeleafOperationsController.PreviewRequest(
                projectRoot.toString(), Map.of(
                        "a.html", "generated",
                        "blocked/b.html", "generated")));
        mcp.approveThymeleafProject("shared", preview.operation().operationId(), preview.previewHash());

        assertThatThrownBy(() -> mcp.applyThymeleafProject("shared", preview.operation().operationId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPLY_ROLLED_BACK");

        assertThat(first).hasContent("original");
        assertThat(rest.report(preview.operation().operationId()).getBody().operation().status())
                .isEqualTo(ProjectOperationStatus.FAILED);
    }
}
