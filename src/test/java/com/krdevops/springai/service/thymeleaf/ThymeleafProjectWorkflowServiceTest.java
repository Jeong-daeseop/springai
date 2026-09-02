package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.artifact.FilesystemArtifactStore;
import com.krdevops.springai.service.artifact.RecordingArtifactCatalog;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.operation.NoopOperationEventPort;
import com.krdevops.springai.service.operation.NoopOperationLockPort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafProjectWorkflowServiceTest {

    @TempDir Path projectRoot;
    @TempDir Path baselineRoot;
    @TempDir Path artifactStoreRoot;
    @TempDir Path runnerDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ThymeleafProjectWorkflowService workflow;
    private ThymeleafOperationStore sharedStore;
    private RecordingArtifactCatalog catalog;
    private ArtifactService artifactService;

    @BeforeEach
    void setUp() {
        workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(),
                new OperationHashFactory(objectMapper));
        sharedStore = new InMemoryThymeleafOperationStore();
        catalog = new RecordingArtifactCatalog();
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(artifactStoreRoot);
        artifactService = new ArtifactService(new FilesystemArtifactStore(properties), catalog);
    }

    @Test
    void previewApproveApplyValidateWithoutPreApprovalWrite() throws Exception {
        String relative = "src/main/resources/templates/users/list.html";
        String html = "<div><form th:action=\"/users\"></form></div>";

        var preview = workflow.preview(projectRoot, Map.of(relative, html));
        Path target = projectRoot.resolve(relative);
        assertThat(preview.operation().status()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(target).doesNotExist();
        assertThatThrownBy(() -> workflow.approve(preview.operation().operationId(), "bad-hash"))
                .hasMessageContaining("PREVIEW_HASH_MISMATCH");

        var approved = workflow.approve(preview.operation().operationId(), preview.previewHash());
        assertThat(approved.operation().status()).isEqualTo(ProjectOperationStatus.APPROVED);
        assertThat(target).doesNotExist();

        var applied = workflow.apply(preview.operation().operationId());
        assertThat(applied.operation().status()).isEqualTo(ProjectOperationStatus.APPLIED);
        assertThat(target).hasContent(html);

        var validated = workflow.revalidate(preview.operation().operationId());
        assertThat(validated.operation().status()).isEqualTo(ProjectOperationStatus.VALIDATED);
    }

    @Test
    void sourceRevisionDriftProducesConflictWithoutOverwrite() throws Exception {
        String relative = "src/main/resources/templates/users/list.html";
        Path target = projectRoot.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, "<div>original</div>");

        var preview = workflow.preview(projectRoot, Map.of(relative, "<div>generated</div>"));
        workflow.approve(preview.operation().operationId(), preview.previewHash());
        Files.writeString(target, "<div>user-change</div>");

        var result = workflow.apply(preview.operation().operationId());

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(result.operation().conflictingFiles()).containsExactly(relative);
        assertThat(target).hasContent("<div>user-change</div>");
    }

    @Test
    void pathTraversalIsRejectedBeforePreview() {
        assertThatThrownBy(() -> workflow.preview(projectRoot, Map.of("../escape.html", "<div/>")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PATH_ESCAPE");
    }

    @Test
    void symbolicLinkParentIsRejectedBeforePreview() throws Exception {
        Path outside = Files.createTempDirectory("thymeleaf-outside-");
        Files.createSymbolicLink(projectRoot.resolve("linked"), outside);

        assertThatThrownBy(() -> workflow.preview(
                projectRoot, Map.of("linked/escape.html", "<div/>")))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("PATH_ESCAPE");
        assertThat(outside.resolve("escape.html")).doesNotExist();
    }

    @Test
    void forbiddenBusinessRuleInDesignMdStopsBeforePreview() throws Exception {
        Files.writeString(projectRoot.resolve("DESIGN.md"), """
                ---
                schemaVersion: "1.0"
                components:
                  form:
                    route: /admin
                ---
                """);

        assertThatThrownBy(() -> designAwareWorkflow().preview(
                projectRoot, Map.of("templates/list.html", "<div/>")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DESIGN_RULE_GATE_FAILED");
        assertThat(projectRoot.resolve("templates/list.html")).doesNotExist();
    }

    @Test
    void designMdDriftAfterApprovalProducesConflictWithoutWrite() throws Exception {
        Files.writeString(projectRoot.resolve("DESIGN.md"), validDesign("8px"));
        ThymeleafProjectWorkflowService designAware = designAwareWorkflow();
        String relative = "templates/list.html";
        var preview = designAware.preview(projectRoot, Map.of(relative, "<div></div>"));
        designAware.approve(preview.operation().operationId(), preview.previewHash());
        Files.writeString(projectRoot.resolve("DESIGN.md"), validDesign("12px"));

        var result = designAware.apply(preview.operation().operationId());

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(result.operation().conflictingFiles()).contains("DESIGN.md");
        assertThat(projectRoot.resolve(relative)).doesNotExist();
    }

    @Test
    void validationFailureCannotBeApproved() {
        var preview = workflow.preview(projectRoot, Map.of(
                "templates/broken.html", "<div style=\"width: 1600px\">"));

        assertThat(preview.operation().validationErrors()).isNotEmpty();
        assertThatThrownBy(() -> workflow.approve(
                preview.operation().operationId(), preview.previewHash()))
                .hasMessageContaining("PREVIEW_VALIDATION_FAILED");
    }

    @Test
    void hardcodedDesignLiteralBlocksApprovalAndLeavesProjectUnchanged() {
        String relative = "templates/hardcoded.html";
        var preview = workflow.preview(projectRoot, Map.of(
                relative, "<div style=\"color:#0b5fff; padding:12px; border-radius:8px\">x</div>"));

        assertThat(preview.operation().status()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(preview.operation().validationErrors())
                .hasSize(3)
                .allMatch(error -> error.contains("DESIGN_TOKEN_HARDCODED"));
        assertThatThrownBy(() -> workflow.approve(
                preview.operation().operationId(), preview.previewHash()))
                .hasMessageContaining("PREVIEW_VALIDATION_FAILED");
        assertThat(projectRoot.resolve(relative)).doesNotExist();
    }

    @Test
    void secondFileFailureRollsBackFirstFile() throws Exception {
        Path first = projectRoot.resolve("a.html");
        Files.writeString(first, "<div>original</div>");
        var preview = workflow.preview(projectRoot, Map.of(
                "a.html", "<div>generated</div>",
                "blocked/b.html", "<div>second</div>"));
        workflow.approve(preview.operation().operationId(), preview.previewHash());
        Files.writeString(projectRoot.resolve("blocked"), "parent-is-a-file");

        assertThatThrownBy(() -> workflow.apply(preview.operation().operationId()))
                .hasMessageContaining("APPLY_ROLLED_BACK");
        assertThat(first).hasContent("<div>original</div>");
        assertThat(workflow.find(preview.operation().operationId()).orElseThrow()
                .operation().status()).isEqualTo(ProjectOperationStatus.FAILED);
    }

    /** ARCH-0811: 브라우저 Gate가 통과하면 VALIDATED로 전이하고 파일별 ValidationReport가 남는다. */
    @Test
    void browserGatePassPersistsValidationReportAndValidates() throws Exception {
        String relative = "templates/list.html";
        var applied = appliedOperation(relative);
        var browserWorkflow = browserAwareWorkflow("PASSED");

        var result = browserWorkflow.revalidate(applied, browserOptions(relative));

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.VALIDATED);
        var reports = catalog.ofType("THYMELEAF_VALIDATION_REPORT");
        assertThat(reports).hasSize(1);
        var report = objectMapper.readValue(
                artifactService.read(reports.get(0)).orElseThrow(), ValidationReport.class);
        assertThat(report.screenId()).isEqualTo(relative);
        assertThat(report.blocked()).isFalse();
        assertThat(report.results()).extracting(ValidationGateResult::gateType)
                .contains(ValidationGateType.THYMELEAF_PARSE, ValidationGateType.BROWSER_RENDER,
                        ValidationGateType.ACCESSIBILITY, ValidationGateType.VISUAL_PARITY);
    }

    /** ARCH-0810: baseline이 없으면 자동 승격 없이 계속 BLOCK된다. */
    @Test
    void missingBaselineStillBlocksWithoutAutoPromotion() throws Exception {
        String relative = "templates/list.html";
        var applied = appliedOperation(relative);
        var browserWorkflow = browserAwareWorkflow("BASELINE_MISSING");

        var result = browserWorkflow.revalidate(applied, browserOptions(relative));

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.FAILED);
        assertThat(result.operation().validationErrors())
                .anySatisfy(error -> assertThat(error).contains("기준 이미지가 없습니다."));
    }

    /** 브라우저 Gate가 없는 호환 인스턴스는 옵션을 조용히 무시하지 않고 실패시킨다. */
    @Test
    void browserOptionsWithoutConfiguredGateFailsLoudly() throws Exception {
        String relative = "templates/list.html";
        var preview = workflow.preview(projectRoot, Map.of(relative, "<div></div>"));
        workflow.approve(preview.operation().operationId(), preview.previewHash());
        workflow.apply(preview.operation().operationId());

        assertThatThrownBy(() -> workflow.revalidate(
                preview.operation().operationId(), browserOptions(relative)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("THYMELEAF_BROWSER_GATE_NOT_CONFIGURED");
    }

    /** 옵션이 비어 있으면 브라우저 Gate를 아예 실행하지 않는다(무인자 오버로드와 동일). */
    @Test
    void emptyBrowserOptionsBehaveExactlyLikeLegacyRevalidate() throws Exception {
        String relative = "templates/list.html";
        var preview = workflow.preview(projectRoot, Map.of(relative, "<div></div>"));
        workflow.approve(preview.operation().operationId(), preview.previewHash());
        workflow.apply(preview.operation().operationId());

        var result = workflow.revalidate(preview.operation().operationId(),
                new ThymeleafProjectWorkflowService.RevalidateBrowserOptions(List.of()));

        assertThat(result.operation().status()).isEqualTo(ProjectOperationStatus.VALIDATED);
    }

    private ThymeleafProjectWorkflowService.RevalidateBrowserOptions browserOptions(String relative) {
        return new ThymeleafProjectWorkflowService.RevalidateBrowserOptions(List.of(
                new ThymeleafProjectWorkflowService.BrowserScreenValidationRequest(
                        "users-list", relative, null, "<html><body>list</body></html>", List.of(), null)));
    }

    private String appliedOperation(String relative) {
        var browserWorkflow = browserAwareWorkflow("PASSED");
        var preview = browserWorkflow.preview(projectRoot, Map.of(relative, "<div></div>"));
        browserWorkflow.approve(preview.operation().operationId(), preview.previewHash());
        browserWorkflow.apply(preview.operation().operationId());
        return preview.operation().operationId();
    }

    /**
     * 같은 store/artifact catalog를 공유하는 인스턴스를 만들어야 preview→apply→revalidate가 한
     * Operation 위에서 이어진다. runner는 실제 Chromium 대신 지정한 visualStatus를 돌려주는 대역이다.
     */
    private ThymeleafProjectWorkflowService browserAwareWorkflow(String visualStatus) {
        try {
            Path script = Files.createTempFile(runnerDirectory, "fake-browser-gate-", ".mjs");
            Files.writeString(script, """
                    import fs from 'node:fs';
                    const requestPath = process.argv[process.argv.indexOf('--request') + 1];
                    const request = JSON.parse(fs.readFileSync(requestPath, 'utf8'));
                    const visualStatus = '%s';
                    const viewports = [['desktop',1440,1200],['tablet',768,1024],['mobile',390,844]]
                      .map(([name, width, height]) => ({ name, width, height,
                        renderStatus: 'PASSED', accessibilityStatus: 'PASSED', visualStatus,
                        renderIssues: [], accessibilityIssues: [], violations: [],
                        screenshotPath: null, baselinePath: null, diffPath: null, differenceRatio: null,
                        visualIssues: visualStatus === 'PASSED' ? [] : ['기준 이미지가 없습니다.'],
                        durationMs: 1 }));
                    process.stdout.write(JSON.stringify({
                      schemaVersion: 'browser-validation-report-v1', screenId: request.screenId,
                      blocked: visualStatus !== 'PASSED', createdAt: '2026-08-07T00:00:00Z',
                      browser: { engine: 'fake', version: '0' }, reportPath: null, viewports }));
                    """.formatted(visualStatus));
            OperationHashFactory hashFactory = new OperationHashFactory(objectMapper);
            return new ThymeleafProjectWorkflowService(
                    new ProjectOperationStateService(), new ValidationGateExecutor(), hashFactory, null,
                    sharedStore, new NoopOperationEventPort(), new NoopOperationLockPort(), artifactService,
                    new SafePathResolver(),
                    new FileSystemApprovedProjectWritePort(new SafePathResolver(), hashFactory),
                    new BrowserValidationGateExecutor(objectMapper, "node", script, Duration.ofSeconds(20)),
                    new BrowserGateDirectoryResolver(new SafePathResolver(), baselineRoot));
        } catch (Exception exception) {
            throw new IllegalStateException("대역 runner 준비 실패", exception);
        }
    }

    private ThymeleafProjectWorkflowService designAwareWorkflow() {
        OperationHashFactory hashFactory = new OperationHashFactory(
                new ObjectMapper().findAndRegisterModules());
        return new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(), hashFactory,
                new DesignMdRuleLoader(new LegacySourceInventoryService(hashFactory),
                        new GenerationIssueFactory()));
    }

    private String validDesign(String spacing) {
        return """
                ---
                schemaVersion: "1.0"
                spacing:
                  medium: "%s"
                ---
                """.formatted(spacing);
    }

    @Test
    void previewFlagsKrdsClassesWhenAssetsMissing() {
        String relative = "src/main/resources/templates/users/list.html";
        String html = "<a class=\"krds-btn primary medium\">등록</a>";

        var preview = workflow.preview(projectRoot, Map.of(relative, html));

        assertThat(preview.operation().status()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(preview.operation().validationErrors())
                .anyMatch(error -> error.contains("KRDS_ASSETS_MISSING"));
        assertThatThrownBy(() -> workflow.approve(preview.operation().operationId(), preview.previewHash()))
                .hasMessageContaining("THYMELEAF_PREVIEW_VALIDATION_FAILED");
    }

    @Test
    void previewPassesWhenKrdsAssetsAlreadyDeployed() throws Exception {
        createBootKrdsAssets(projectRoot);
        String relative = "src/main/resources/templates/users/list.html";
        String html = "<a class=\"krds-btn primary medium\">등록</a>";

        var preview = workflow.preview(projectRoot, Map.of(relative, html));

        assertThat(preview.operation().status()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(preview.operation().validationErrors())
                .noneMatch(error -> error.contains("KRDS_ASSETS_MISSING"));
    }

    @Test
    void previewIgnoresKrdsAssetCheckWhenScreenHasNoKrdsClasses() {
        String relative = "src/main/resources/templates/users/list.html";
        String html = "<div><form th:action=\"/users\"></form></div>";

        var preview = workflow.preview(projectRoot, Map.of(relative, html));

        assertThat(preview.operation().validationErrors())
                .noneMatch(error -> error.contains("KRDS_ASSETS_MISSING"));
    }

    private void createBootKrdsAssets(Path root) throws Exception {
        Path css = root.resolve("src/main/resources/static/resources/css/styles.css");
        Path bundle = root.resolve("src/main/resources/static/resources/css/_ds_bundle.css");
        Path js = root.resolve("src/main/resources/static/resources/js/krds.min.js");
        Files.createDirectories(css.getParent());
        Files.createDirectories(js.getParent());
        Files.writeString(css, "/* styles */");
        Files.writeString(bundle, "/* bundle */");
        Files.writeString(js, "/* js */");
    }
}
