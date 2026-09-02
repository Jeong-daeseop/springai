package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import com.krdevops.springai.service.ScreenSpecificationService;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.artifact.FilesystemArtifactStore;
import com.krdevops.springai.service.artifact.RecordingArtifactCatalog;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.operation.NoopOperationEventPort;
import com.krdevops.springai.service.operation.NoopOperationLockPort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** WP6 생성 진입점이 실제 Reader→Assembler→Composer→승인 Workflow를 한 흐름으로 잇는지 검증한다. */
class ThymeleafBindingGenerationServiceTest {

    private static final Path FIXTURE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    @TempDir Path projectRoot;
    @TempDir Path artifactRoot;

    private OperationHashFactory hashFactory;
    private ObjectMapper objectMapper;
    private RecordingArtifactCatalog artifactCatalog;
    private ArtifactService artifactService;
    private ThymeleafOperationStore operationStore;
    private ThymeleafProjectWorkflowService workflow;
    private ThymeleafBindingGenerationService service;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        hashFactory = new OperationHashFactory(objectMapper);
        artifactCatalog = new RecordingArtifactCatalog();
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(artifactRoot);
        artifactService = new ArtifactService(
                new FilesystemArtifactStore(properties), artifactCatalog);
        SafePathResolver pathResolver = new SafePathResolver();
        operationStore = new InMemoryThymeleafOperationStore();
        workflow = new ThymeleafProjectWorkflowService(
                new ProjectOperationStateService(), new ValidationGateExecutor(), hashFactory, null,
                operationStore, new NoopOperationEventPort(),
                new NoopOperationLockPort(), artifactService, pathResolver,
                new FileSystemApprovedProjectWritePort(pathResolver, hashFactory), null, null);
        service = serviceWith(workflow);

        Path legacy = Files.createDirectories(projectRoot.resolve("legacy"));
        Files.copy(FIXTURE.resolve("EgovEmployerList.jsp"), legacy.resolve("EgovEmployerList.jsp"));
        Files.copy(FIXTURE.resolve("EgovEmployerController.java"), legacy.resolve("EgovEmployerController.java"));
        Files.copy(FIXTURE.resolve("EmployerVO.java"), legacy.resolve("EmployerVO.java"));

        // BindingComposer가 legacy-thymeleaf 템플릿으로 만드는 화면은 krds-* 클래스를 쓰므로,
        // ThymeleafProjectWorkflowService의 KRDS 자산 검증 게이트를 통과하려면 배치돼 있어야 한다.
        Path css = projectRoot.resolve("src/main/resources/static/resources/css/styles.css");
        Path bundle = projectRoot.resolve("src/main/resources/static/resources/css/_ds_bundle.css");
        Path js = projectRoot.resolve("src/main/resources/static/resources/js/krds.min.js");
        Files.createDirectories(css.getParent());
        Files.createDirectories(js.getParent());
        Files.writeString(css, "/* styles */");
        Files.writeString(bundle, "/* bundle */");
        Files.writeString(js, "/* js */");
    }

    @Test
    void realSourcesCreatePreviewReadyOperationAndArtifactWithoutWritingProject() {
        ThymeleafBindingGenerationService.BindingPreviewResult result = service.preview(validRequest());

        assertThat(result.successful()).isTrue();
        assertThat(result.completedStage()).isEqualTo("PREVIEW_READY");
        assertThat(result.bindingContract().sourceRevision().revisionToken()).matches("^[a-f0-9]{64}$");
        assertThat(result.workflow().operation().status()).isEqualTo(ProjectOperationStatus.PREVIEW_READY);
        assertThat(result.workflow().operation().previewArtifacts().get(result.outputRelativePath()))
                .contains("th:each=\"item : ${resultList}\"")
                .contains("th:action=\"@{/emp/employerList.do}\"");
        assertThat(projectRoot.resolve(result.outputRelativePath())).doesNotExist();
        assertThat(artifactCatalog.ofType("THYMELEAF_PREVIEW")).hasSize(1);
        assertThat(artifactCatalog.ofType("THYMELEAF_BINDING_CONTRACT")).hasSize(1);
        var snapshot = operationStore.findLatest(result.workflow().operation().operationId()).orElseThrow();
        assertThat(snapshot.bindingContract()).isEqualTo(result.bindingContract());
        assertThat(snapshot.legacySourceManifest().files()).extracting("relativePath")
                .containsExactly("legacy/EgovEmployerController.java", "legacy/EgovEmployerList.jsp",
                        "legacy/EmployerVO.java");
        assertThat(snapshot.legacySourceManifest().fingerprint())
                .isEqualTo(result.bindingContract().sourceRevision().revisionToken());
    }

    /**
     * R6-053: 실제 LIST 화면 fixture(`th:each`·`/emp/employerList.do`)에 일부러 잘못된
     * screenRole(FORM)을 넘기면, 소스 증거가 가리키는 유형과 어긋난다는 WARNING이 남아야 한다.
     * Preview 자체는 막히지 않는다(자동 판정이 필수 입력을 대체하지 않음).
     */
    @Test
    void mismatchedScreenRoleAgainstSourceEvidenceProducesWarningWithoutBlockingPreview() {
        ThymeleafBindingPreviewRequest mismatched = new ThymeleafBindingPreviewRequest(
                projectRoot.toString(), "employer-list", LegacyScreenRole.FORM,
                "legacy/EgovEmployerList.jsp", "legacy/EgovEmployerController.java", "legacy/EmployerVO.java",
                null, null, "src/main/resources/templates/employer/EgovEmployerList.html",
                "직원 목록", "layout/default", null, "/emp/employerRegistView.do");

        ThymeleafBindingGenerationService.BindingPreviewResult result = service.preview(mismatched);

        assertThat(result.successful()).isTrue();
        assertThat(result.issues()).extracting(GenerationIssue::code)
                .contains("SCREEN_ROLE_MISMATCH_WITH_SOURCE_EVIDENCE");
    }

    /**
     * R6-T20: BINDING_CONTRACT 단계에서 FATAL(FORM_FIELD_WITHOUT_VO_FIELD)이 나면 파이프라인이
     * 거기서 멈추고 BINDING_COMPOSE/PREVIEW_READY로 넘어가지 않아야 한다 — 이후 단계가 실행됐다는
     * 증거(Artifact·Workflow Operation)가 하나도 남지 않아야 진짜 컷오프다.
     */
    @Test
    void fatalContractIssueStopsPipelineBeforeComposeStageAndLeavesNoLaterArtifacts() throws Exception {
        Path legacy = projectRoot.resolve("legacy");
        Files.copy(FIXTURE.resolve("EgovEmployerRegist.jsp"), legacy.resolve("EgovEmployerRegist.jsp"));
        // ofcpsNm 필드를 뺀 VO — EgovEmployerRegist.jsp의 form:input path="ofcpsNm"이 바인딩할 곳이 없어진다.
        Files.writeString(legacy.resolve("EmployerVOBroken.java"), """
                package egovframework.let.emp.vo;
                public class EmployerVOBroken {
                    private String emplyrId;
                    private String emplyrNm;
                    private String emailAdres;
                    public String getEmplyrId() { return emplyrId; }
                    public void setEmplyrId(String emplyrId) { this.emplyrId = emplyrId; }
                    public String getEmplyrNm() { return emplyrNm; }
                    public void setEmplyrNm(String emplyrNm) { this.emplyrNm = emplyrNm; }
                    public String getEmailAdres() { return emailAdres; }
                    public void setEmailAdres(String emailAdres) { this.emailAdres = emailAdres; }
                }
                """);
        ThymeleafBindingPreviewRequest request = new ThymeleafBindingPreviewRequest(
                projectRoot.toString(), "employer-regist-broken", LegacyScreenRole.FORM,
                "legacy/EgovEmployerRegist.jsp", "legacy/EgovEmployerController.java", "legacy/EmployerVOBroken.java",
                null, null, "src/main/resources/templates/employer/EgovEmployerRegist.html",
                "직원 등록", "layout/default", null, "/emp/employerList.do");

        ThymeleafBindingGenerationService.BindingPreviewResult result = service.preview(request);

        assertThat(result.successful()).isFalse();
        assertThat(result.completedStage()).isEqualTo("BINDING_CONTRACT");
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("FORM_FIELD_WITHOUT_VO_FIELD"));
        // 컷오프 증거: 이후 단계(Compose/Workflow Preview)가 만드는 산출물이 전혀 없다.
        assertThat(result.workflow()).isNull();
        assertThat(artifactCatalog.ofType("THYMELEAF_PREVIEW")).isEmpty();
        assertThat(artifactCatalog.ofType("THYMELEAF_BINDING_CONTRACT")).isEmpty();
    }

    @Test
    void changedLegacySourceAfterApprovalProducesConflictWithoutWritingGeneratedFile() throws Exception {
        var preview = service.preview(validRequest());
        workflow.approve(preview.workflow().operation().operationId(), preview.workflow().previewHash());
        Files.writeString(projectRoot.resolve("legacy/EgovEmployerController.java"),
                Files.readString(projectRoot.resolve("legacy/EgovEmployerController.java"))
                        + "\n// changed after approval\n");

        var applied = workflow.apply(preview.workflow().operation().operationId());

        assertThat(applied.operation().status()).isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(applied.operation().conflictingFiles())
                .containsExactly("LEGACY_SOURCE:legacy/EgovEmployerController.java");
        assertThat(projectRoot.resolve(preview.outputRelativePath())).doesNotExist();
    }

    @Test
    void deletedLegacySourceAfterApprovalProducesConflictWithoutWritingGeneratedFile() throws Exception {
        var preview = service.preview(validRequest());
        workflow.approve(preview.workflow().operation().operationId(), preview.workflow().previewHash());
        Files.delete(projectRoot.resolve("legacy/EmployerVO.java"));

        var applied = workflow.apply(preview.workflow().operation().operationId());

        assertThat(applied.operation().status()).isEqualTo(ProjectOperationStatus.CONFLICT);
        assertThat(applied.operation().conflictingFiles())
                .containsExactly("LEGACY_SOURCE:legacy/EmployerVO.java");
        assertThat(projectRoot.resolve(preview.outputRelativePath())).doesNotExist();
    }

    @Test
    void changedLegacyFingerprintDoesNotReuseOperationEvenWhenGeneratedHtmlIsSame() throws Exception {
        var first = service.preview(validRequest());
        Path controller = projectRoot.resolve("legacy/EgovEmployerController.java");
        Files.writeString(controller, Files.readString(controller) + "\n// hash-only change\n");

        var second = service.preview(validRequest());

        assertThat(second.workflow().operation().previewArtifacts())
                .isEqualTo(first.workflow().operation().previewArtifacts());
        assertThat(second.workflow().previewHash()).isNotEqualTo(first.workflow().previewHash());
        assertThat(second.workflow().operation().operationId())
                .isNotEqualTo(first.workflow().operation().operationId());
    }

    @Test
    void regenerationWithoutChangeAfterApplyDoesNotRequireReview() throws Exception {
        var first = service.preview(validRequest());
        workflow.approve(first.workflow().operation().operationId(), first.workflow().previewHash());
        workflow.apply(first.workflow().operation().operationId());

        var second = service.preview(validRequest());

        assertThat(second.successful()).isTrue();
        assertThat(second.completedStage()).isEqualTo("PREVIEW_READY");
    }

    @Test
    void regenerationWithNewPermissionAnnotationIsBlockedForReview() throws Exception {
        var first = service.preview(validRequest());
        workflow.approve(first.workflow().operation().operationId(), first.workflow().previewHash());
        workflow.apply(first.workflow().operation().operationId());

        Path controller = projectRoot.resolve("legacy/EgovEmployerController.java");
        Files.writeString(controller, Files.readString(controller)
                .replace("import org.springframework.web.bind.annotation.GetMapping;",
                        "import org.springframework.web.bind.annotation.GetMapping;\n"
                                + "import org.springframework.security.access.prepost.PreAuthorize;")
                .replace("@GetMapping(\"/emp/employerList.do\")",
                        "@PreAuthorize(\"hasRole('ADMIN')\")\n    @GetMapping(\"/emp/employerList.do\")"));

        var second = service.preview(validRequest());

        assertThat(second.successful()).isFalse();
        assertThat(second.completedStage()).isEqualTo("REGENERATION_DIFF");
        assertThat(second.issues()).extracting("code")
                .contains("REGENERATION_PERMISSION_OR_CSRF_CHANGED");
    }

    @Test
    void revalidateAutomaticallyRunsContractAndTemplateEngineGates() throws Exception {
        var preview = service.preview(validRequest());
        String operationId = preview.workflow().operation().operationId();
        workflow.approve(operationId, preview.workflow().previewHash());
        workflow.apply(operationId);

        var validated = workflow.revalidate(operationId);

        assertThat(validated.operation().status()).isEqualTo(ProjectOperationStatus.VALIDATED);
        var reports = artifactCatalog.ofType("THYMELEAF_VALIDATION_REPORT");
        assertThat(reports).hasSize(1);
        ValidationReport report = objectMapper.readValue(
                artifactService.read(reports.get(0)).orElseThrow(), ValidationReport.class);
        assertThat(report.screenId()).isEqualTo(preview.outputRelativePath());
        assertThat(report.results()).extracting(ValidationGateResult::gateType)
                .containsExactly(
                        ValidationGateType.THYMELEAF_PARSE,
                        ValidationGateType.TEMPLATE_ENGINE_RENDER,
                        ValidationGateType.BINDING_VALIDATION,
                        ValidationGateType.ROUTE_PARITY,
                        ValidationGateType.OVERFLOW_CHECK);
        assertThat(report.blocked()).isFalse();
    }

    @Test
    void revalidateBlocksWhenAppliedFileLosesContractBinding() throws Exception {
        var preview = service.preview(validRequest());
        String operationId = preview.workflow().operation().operationId();
        workflow.approve(operationId, preview.workflow().previewHash());
        workflow.apply(operationId);
        String field = preview.bindingContract().displayFieldNames().get(0);
        Path generated = projectRoot.resolve(preview.outputRelativePath());
        Files.writeString(generated, Files.readString(generated)
                .replace("th:text=\"${item." + field + "}\"", "data-removed-binding=\"" + field + "\""));

        var failed = workflow.revalidate(operationId);

        assertThat(failed.operation().status()).isEqualTo(ProjectOperationStatus.FAILED);
        assertThat(failed.operation().validationErrors())
                .anySatisfy(error -> assertThat(error)
                        .contains("바인딩 누락").contains("${item." + field + "}"));
    }

    @Test
    void reviewRequiredContractIsBlockedBeforeWorkflowPreview() throws Exception {
        Files.writeString(projectRoot.resolve("legacy/Review.jsp"), "<p>${result.missingField}</p>");
        Files.writeString(projectRoot.resolve("legacy/ReviewController.java"), """
                import org.springframework.stereotype.Controller;
                import org.springframework.ui.ModelMap;
                import org.springframework.web.bind.annotation.GetMapping;
                @Controller class ReviewController {
                  @GetMapping("/review") String show(ModelMap model) {
                    model.addAttribute("result", new EmployerVO());
                    return "Review";
                  }
                }
                """);
        ThymeleafProjectWorkflowService workflow = mock(ThymeleafProjectWorkflowService.class);
        ThymeleafBindingGenerationService blockedService = serviceWith(workflow);
        ThymeleafBindingPreviewRequest request = new ThymeleafBindingPreviewRequest(
                projectRoot.toString(), "review", LegacyScreenRole.DETAIL,
                "legacy/Review.jsp", "legacy/ReviewController.java", "legacy/EmployerVO.java",
                null, null, "templates/review.html", "검토 화면", "layout/default", null, null);

        ThymeleafBindingGenerationService.BindingPreviewResult result = blockedService.preview(request);

        assertThat(result.successful()).isFalse();
        assertThat(result.completedStage()).isEqualTo("BINDING_COMPOSE");
        assertThat(result.issues()).extracting("code")
                .contains("BINDING_REVIEW_REQUIRED_BLOCKS_COMPOSE");
        org.mockito.Mockito.verifyNoInteractions(workflow);
    }

    @Test
    void persistedScreenSpecificationMustBeApproved() {
        ScreenSpecificationService specifications = mock(ScreenSpecificationService.class);
        ScreenSpecification draft = mock(ScreenSpecification.class);
        when(draft.id()).thenReturn("spec-draft");
        when(draft.status()).thenReturn(ScreenSpecStatus.DRAFT);
        when(specifications.get("spec-draft")).thenReturn(draft);
        ThymeleafProjectWorkflowService workflow = mock(ThymeleafProjectWorkflowService.class);
        ThymeleafBindingGenerationService guardedService = serviceWith(workflow, specifications);
        ThymeleafBindingPreviewRequest base = validRequest();
        ThymeleafBindingPreviewRequest request = new ThymeleafBindingPreviewRequest(
                base.projectRootPath(), base.screenId(), base.screenRole(), base.jspRelativePath(),
                base.controllerRelativePath(), base.voRelativePath(), base.voSuperclassRelativePath(),
                base.secondaryVoRelativePath(), base.outputRelativePath(), base.pageTitle(), base.layoutView(),
                "spec-draft", base.registRoute());

        assertThatThrownBy(() -> guardedService.preview(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED 화면명세만");
        org.mockito.Mockito.verifyNoInteractions(workflow);
    }

    /** R6-057: designSystemProfileId가 주어지면 해석된 Design Token이 provenance 주석으로 반영된다. */
    @Test
    void designSystemProfileIdResolvesTokensIntoGeneratedHtml() {
        CompanyDesignTokenResolver designTokenResolver = mock(CompanyDesignTokenResolver.class);
        when(designTokenResolver.resolve("krds", null)).thenReturn(
                com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult.success(
                        new com.krdevops.springai.model.thymeleaf.ResolvedDesignTokens(
                                "krds", "2.2.2", null,
                                java.util.Map.of("primary-60", "--krds-color-primary-60"),
                                java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
                                java.util.Map.of(), java.util.List.of()),
                        java.util.List.of()));
        ThymeleafBindingGenerationService withTokens = serviceWith(workflow, designTokenResolver);
        ThymeleafBindingPreviewRequest base = validRequest();
        ThymeleafBindingPreviewRequest request = new ThymeleafBindingPreviewRequest(
                base.projectRootPath(), base.screenId(), base.screenRole(), base.jspRelativePath(),
                base.controllerRelativePath(), base.voRelativePath(), base.voSuperclassRelativePath(),
                base.secondaryVoRelativePath(), base.outputRelativePath(), base.pageTitle(), base.layoutView(),
                base.screenSpecificationId(), base.registRoute(), "krds");

        var result = withTokens.preview(request);

        assertThat(result.successful()).isTrue();
        assertThat(result.workflow().operation().previewArtifacts().get(result.outputRelativePath()))
                .contains("--krds-color-primary-60");
    }

    /**
     * R6-062: 이전에는 DESIGN.md가 있어도 항상 {@code null}이 하드코딩돼 있어
     * {@link CompanyDesignTokenResolver}의 override 병합 로직(구현·테스트는 있었음)이 실제
     * 생성 파이프라인에서 한 번도 실행되지 않았다 — 실제 DESIGN.md 파일 + 실제
     * CompanyDesignTokenResolver(mock 아님)로 화면 Override가 정말로 생성 결과에 반영되는지
     * end-to-end로 검증한다.
     */
    @Test
    void designMdColorOverrideIsMergedIntoGeneratedHtmlProvenance() throws Exception {
        Files.writeString(projectRoot.resolve("DESIGN.md"), """
                ---
                schemaVersion: "1.0"
                colors:
                  screenOverride: "--krds-color-screen-override-test"
                ---

                # 화면별 Design 규칙
                """);
        var queryService = mock(com.krdevops.springai.service.designsystem.DesignSystemQueryService.class);
        when(queryService.findLatestProfile("krds")).thenReturn(
                new com.krdevops.springai.model.designsystem.DesignSystemProfile(
                        "krds", "KRDS", "2.2.2", "registry-1", "file-key",
                        com.krdevops.springai.model.designsystem.DesignSystemProfile.Status.PUBLISHED,
                        java.util.Map.of(), java.util.Map.of()));
        when(queryService.findLatestRegistry("krds")).thenReturn(
                new com.krdevops.springai.model.designsystem.ComponentRegistry(
                        "krds", "2.2.2", "registry-1", null, java.util.Map.of(), java.util.Map.of()));
        CompanyDesignTokenResolver realResolver =
                new CompanyDesignTokenResolver(queryService, new GenerationIssueFactory());
        ThymeleafBindingGenerationService withRealTokens = serviceWith(workflow, realResolver);
        ThymeleafBindingPreviewRequest base = validRequest();
        ThymeleafBindingPreviewRequest request = new ThymeleafBindingPreviewRequest(
                base.projectRootPath(), base.screenId(), base.screenRole(), base.jspRelativePath(),
                base.controllerRelativePath(), base.voRelativePath(), base.voSuperclassRelativePath(),
                base.secondaryVoRelativePath(), base.outputRelativePath(), base.pageTitle(), base.layoutView(),
                base.screenSpecificationId(), base.registRoute(), "krds");

        var result = withRealTokens.preview(request);

        assertThat(result.successful()).isTrue();
        assertThat(result.workflow().operation().previewArtifacts().get(result.outputRelativePath()))
                .contains("--krds-color-screen-override-test");
    }

    /** Design Token 해석이 실패해도(FATAL) Preview 전체를 막지 않고 토큰 없이 계속 진행한다. */
    @Test
    void failedDesignTokenResolutionDoesNotBlockPreview() {
        CompanyDesignTokenResolver designTokenResolver = mock(CompanyDesignTokenResolver.class);
        when(designTokenResolver.resolve("missing-profile", null)).thenReturn(
                com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult.failure(
                        java.util.List.of(new com.krdevops.springai.model.contract.GenerationIssue(
                                "DESIGN_SYSTEM_PROFILE_NOT_FOUND",
                                com.krdevops.springai.model.contract.GenerationIssue.Severity.FATAL,
                                "R6-056", null, "찾을 수 없음", null))));
        ThymeleafBindingGenerationService withTokens = serviceWith(workflow, designTokenResolver);
        ThymeleafBindingPreviewRequest base = validRequest();
        ThymeleafBindingPreviewRequest request = new ThymeleafBindingPreviewRequest(
                base.projectRootPath(), base.screenId(), base.screenRole(), base.jspRelativePath(),
                base.controllerRelativePath(), base.voRelativePath(), base.voSuperclassRelativePath(),
                base.secondaryVoRelativePath(), base.outputRelativePath(), base.pageTitle(), base.layoutView(),
                base.screenSpecificationId(), base.registRoute(), "missing-profile");

        var result = withTokens.preview(request);

        assertThat(result.successful()).isTrue();
        assertThat(result.workflow().operation().previewArtifacts().get(result.outputRelativePath()))
                .doesNotContain("egov-design-token-provenance");
    }

    private ThymeleafBindingGenerationService serviceWith(ThymeleafProjectWorkflowService workflow) {
        return serviceWith(workflow, mock(ScreenSpecificationService.class), mock(CompanyDesignTokenResolver.class));
    }

    private ThymeleafBindingGenerationService serviceWith(
            ThymeleafProjectWorkflowService workflow, ScreenSpecificationService specifications) {
        return serviceWith(workflow, specifications, mock(CompanyDesignTokenResolver.class));
    }

    private ThymeleafBindingGenerationService serviceWith(
            ThymeleafProjectWorkflowService workflow, CompanyDesignTokenResolver designTokenResolver) {
        return serviceWith(workflow, mock(ScreenSpecificationService.class), designTokenResolver);
    }

    private ThymeleafBindingGenerationService serviceWith(
            ThymeleafProjectWorkflowService workflow, ScreenSpecificationService specifications,
            CompanyDesignTokenResolver designTokenResolver) {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_33);
        try {
            configuration.setDirectoryForTemplateLoading(
                    new File(new File("").getAbsolutePath(), "src/main/resources/templates"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        GenerationIssueFactory issueFactory = new GenerationIssueFactory();
        LegacySourceInventoryService legacyInventory = new LegacySourceInventoryService(hashFactory);
        return new ThymeleafBindingGenerationService(
                legacyInventory, new JspSourceReader(),
                new ControllerSourceReader(), new VoSourceReader(), new LegacyScreenRoleResolver(),
                new DesignMdRuleLoader(legacyInventory, issueFactory),
                new BindingContractAssembler(issueFactory), new BindingComposer(configuration, issueFactory),
                designTokenResolver,
                specifications, workflow, new RegenerationDiffService());
    }

    private ThymeleafBindingPreviewRequest validRequest() {
        return new ThymeleafBindingPreviewRequest(
                projectRoot.toString(), "employer-list", LegacyScreenRole.LIST,
                "legacy/EgovEmployerList.jsp", "legacy/EgovEmployerController.java", "legacy/EmployerVO.java",
                null, null, "src/main/resources/templates/employer/EgovEmployerList.html",
                "직원 목록", "layout/default", null, "/emp/employerRegistView.do");
    }
}
