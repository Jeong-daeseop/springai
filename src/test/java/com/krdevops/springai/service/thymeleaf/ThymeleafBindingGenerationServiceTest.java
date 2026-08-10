package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.ArtifactStoreProperties;
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

    private ThymeleafBindingGenerationService serviceWith(ThymeleafProjectWorkflowService workflow) {
        return serviceWith(workflow, mock(ScreenSpecificationService.class));
    }

    private ThymeleafBindingGenerationService serviceWith(
            ThymeleafProjectWorkflowService workflow, ScreenSpecificationService specifications) {
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
        return new ThymeleafBindingGenerationService(
                new LegacySourceInventoryService(hashFactory), new JspSourceReader(),
                new ControllerSourceReader(), new VoSourceReader(),
                new BindingContractAssembler(issueFactory), new BindingComposer(configuration, issueFactory),
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
