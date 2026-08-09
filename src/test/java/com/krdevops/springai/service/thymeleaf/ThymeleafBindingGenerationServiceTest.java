package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingPreviewRequest;
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
    private RecordingArtifactCatalog artifactCatalog;
    private ThymeleafOperationStore operationStore;
    private ThymeleafProjectWorkflowService workflow;
    private ThymeleafBindingGenerationService service;

    @BeforeEach
    void setUp() throws Exception {
        hashFactory = new OperationHashFactory(new ObjectMapper().findAndRegisterModules());
        artifactCatalog = new RecordingArtifactCatalog();
        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(artifactRoot);
        ArtifactService artifactService = new ArtifactService(
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
                specifications, workflow);
    }

    private ThymeleafBindingPreviewRequest validRequest() {
        return new ThymeleafBindingPreviewRequest(
                projectRoot.toString(), "employer-list", LegacyScreenRole.LIST,
                "legacy/EgovEmployerList.jsp", "legacy/EgovEmployerController.java", "legacy/EmployerVO.java",
                null, null, "src/main/resources/templates/employer/EgovEmployerList.html",
                "직원 목록", "layout/default", null, "/emp/employerRegistView.do");
    }
}
