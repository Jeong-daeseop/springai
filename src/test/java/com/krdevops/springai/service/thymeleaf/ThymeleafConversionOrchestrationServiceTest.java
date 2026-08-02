package com.krdevops.springai.service.thymeleaf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ThymeleafConversionOperationRepository;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation;
import com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult;
import com.krdevops.springai.service.ThymeleafRenderValidator;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.contract.OperationHashFactory;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * I-5B/C E2E: I-2 golden fixture로 실제 임시 디렉터리에 파일을 쓰는 데까지 전체 파이프라인을 돌려
 * 완료 게이트(승인 전 미쓰기, source revision 변경 시 CONFLICT, 재적용 시 백업, Apply 후 재검증)를
 * 검증한다.
 */
class ThymeleafConversionOrchestrationServiceTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final GenerationIssueFactory issueFactory = new GenerationIssueFactory();
    private final OperationHashFactory operationHashFactory = new OperationHashFactory(objectMapper);

    private final JspSourceReader jspReader = new JspSourceReader();
    private final ControllerSourceReader controllerReader = new ControllerSourceReader();
    private final VoSourceReader voReader = new VoSourceReader();
    private final LegacyBindingContractAssembler assembler = new LegacyBindingContractAssembler(issueFactory);
    private final ThymeleafSkeletonPlanner skeletonPlanner = new ThymeleafSkeletonPlanner();
    private final LegacyThymeleafViewComposer viewComposer = new LegacyThymeleafViewComposer(issueFactory);
    private final LegacyThymeleafRenderer renderer = new LegacyThymeleafRenderer(freemarkerConfiguration());
    private final ThymeleafConversionOperationStateService operationStateService =
            new ThymeleafConversionOperationStateService();
    private final ThymeleafConversionOperationRepository repository = new ThymeleafConversionOperationRepository(
            jdbcTemplate, objectMapper, operationHashFactory, operationStateService);
    private final LegacySourceInventoryService inventoryService =
            new LegacySourceInventoryService(operationHashFactory);

    private ThymeleafRenderValidator renderValidator;
    private ThymeleafConversionOrchestrationService orchestration;

    @TempDir
    Path targetProjectRoot;

    @BeforeEach
    void setUp() {
        renderValidator = Mockito.mock(ThymeleafRenderValidator.class);
        when(renderValidator.validateDirectory(anyString()))
                .thenReturn(new ThymeleafRenderValidator.RenderReport(1, 1, List.of()));
        orchestration = new ThymeleafConversionOrchestrationService(
                assembler, skeletonPlanner, viewComposer, renderer, repository, inventoryService,
                renderValidator, operationHashFactory, issueFactory);
    }

    @Test
    void fullPipelineWritesFileAndReachesValidated() throws IOException {
        repository.createTableIfNotExists();
        String screenId = "emp-list-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = analysisFor(screenId, LegacyScreenRole.LIST, "rev-1");

        ThymeleafGenerationStageResult<ThymeleafConversionOperation> previewResult =
                orchestration.analyzeAndPreview(analysis, "직원 목록", "employer/EgovEmployerList.html");
        assertThat(previewResult.successful()).as("issues: %s", previewResult.issues()).isTrue();
        assertThat(previewResult.value().status()).isEqualTo(ThymeleafConversionOperationStatus.PREVIEW_READY);
        assertThat(Files.exists(targetProjectRoot.resolve("employer/EgovEmployerList.html"))).isFalse();

        ThymeleafConversionOperation approved = orchestration.approve(previewResult.value().operationId());
        assertThat(approved.status()).isEqualTo(ThymeleafConversionOperationStatus.APPROVED);
        assertThat(Files.exists(targetProjectRoot.resolve("employer/EgovEmployerList.html")))
                .as("승인만으로는 파일이 쓰이면 안 된다").isFalse();

        ThymeleafGenerationStageResult<ThymeleafConversionOperation> applyResult =
                orchestration.apply(approved.operationId(), targetProjectRoot, analysis);
        assertThat(applyResult.successful()).as("issues: %s", applyResult.issues()).isTrue();
        assertThat(applyResult.value().status()).isEqualTo(ThymeleafConversionOperationStatus.VALIDATED);

        Path written = targetProjectRoot.resolve("employer/EgovEmployerList.html");
        assertThat(Files.exists(written)).isTrue();
        assertThat(Files.readString(written)).contains("th:each=\"item : ${resultList}\"");
        assertThat(applyResult.value().artifacts()).anyMatch(a -> a.artifactType().equals("THYMELEAF_APPLIED_FILE"));

        cleanup(previewResult.value());
    }

    @Test
    void applyBeforeApprovalIsRejectedAndFileIsNeverWritten() throws IOException {
        repository.createTableIfNotExists();
        String screenId = "emp-list-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = analysisFor(screenId, LegacyScreenRole.LIST, "rev-1");
        ThymeleafGenerationStageResult<ThymeleafConversionOperation> previewResult =
                orchestration.analyzeAndPreview(analysis, "직원 목록", "employer/EgovEmployerList.html");

        try {
            assertThatThrownBy(() -> orchestration.apply(
                    previewResult.value().operationId(), targetProjectRoot, analysis))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("THYMELEAF_OPERATION_NOT_APPROVED");
            assertThat(Files.exists(targetProjectRoot.resolve("employer/EgovEmployerList.html"))).isFalse();
        } finally {
            cleanup(previewResult.value());
        }
    }

    @Test
    void applyDetectsSourceRevisionConflictAndDoesNotWriteFile() throws IOException {
        repository.createTableIfNotExists();
        String screenId = "emp-list-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = analysisFor(screenId, LegacyScreenRole.LIST, "rev-1");
        ThymeleafGenerationStageResult<ThymeleafConversionOperation> previewResult =
                orchestration.analyzeAndPreview(analysis, "직원 목록", "employer/EgovEmployerList.html");
        ThymeleafConversionOperation approved = orchestration.approve(previewResult.value().operationId());

        LegacyScreenAnalysis changedAnalysis = analysisFor(screenId, LegacyScreenRole.LIST, "rev-2-changed");
        try {
            ThymeleafGenerationStageResult<ThymeleafConversionOperation> applyResult =
                    orchestration.apply(approved.operationId(), targetProjectRoot, changedAnalysis);

            assertThat(applyResult.successful()).isFalse();
            assertThat(applyResult.issues()).anyMatch(issue -> issue.code().equals("SOURCE_REVISION_CHANGED"));
            assertThat(Files.exists(targetProjectRoot.resolve("employer/EgovEmployerList.html"))).isFalse();

            ThymeleafConversionOperation latest = repository.findLatest(approved.operationId()).orElseThrow();
            assertThat(latest.status()).isEqualTo(ThymeleafConversionOperationStatus.CONFLICT);
        } finally {
            cleanup(previewResult.value());
        }
    }

    @Test
    void reapplyingBacksUpExistingFileBeforeOverwriting() throws IOException {
        repository.createTableIfNotExists();
        String screenId = "emp-list-" + UUID.randomUUID();
        Files.createDirectories(targetProjectRoot.resolve("employer"));
        Files.writeString(targetProjectRoot.resolve("employer/EgovEmployerList.html"), "<html>old-manual</html>");

        LegacyScreenAnalysis analysis = analysisFor(screenId, LegacyScreenRole.LIST, "rev-1");
        ThymeleafGenerationStageResult<ThymeleafConversionOperation> previewResult =
                orchestration.analyzeAndPreview(analysis, "직원 목록", "employer/EgovEmployerList.html");
        ThymeleafConversionOperation approved = orchestration.approve(previewResult.value().operationId());

        try {
            ThymeleafGenerationStageResult<ThymeleafConversionOperation> applyResult =
                    orchestration.apply(approved.operationId(), targetProjectRoot, analysis);
            assertThat(applyResult.successful()).as("issues: %s", applyResult.issues()).isTrue();

            assertThat(applyResult.value().artifacts())
                    .anyMatch(a -> a.artifactType().equals("THYMELEAF_APPLY_BACKUP"));
            Path backup = findBackupFile(targetProjectRoot.resolve("employer"));
            assertThat(backup).isNotNull();
            assertThat(Files.readString(backup)).isEqualTo("<html>old-manual</html>");
            assertThat(Files.readString(targetProjectRoot.resolve("employer/EgovEmployerList.html")))
                    .contains("th:each=\"item : ${resultList}\"");
        } finally {
            cleanup(previewResult.value());
        }
    }

    @Test
    void analyzeAndPreviewIsIdempotentForSameContractAndTarget() throws IOException {
        repository.createTableIfNotExists();
        String screenId = "emp-list-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = analysisFor(screenId, LegacyScreenRole.LIST, "rev-1");

        ThymeleafGenerationStageResult<ThymeleafConversionOperation> first =
                orchestration.analyzeAndPreview(analysis, "직원 목록", "employer/EgovEmployerList.html");
        ThymeleafGenerationStageResult<ThymeleafConversionOperation> second =
                orchestration.analyzeAndPreview(analysis, "직원 목록", "employer/EgovEmployerList.html");

        try {
            assertThat(second.value().operationId()).isEqualTo(first.value().operationId());
            assertThat(second.value().revision()).isEqualTo(first.value().revision());
        } finally {
            cleanup(first.value());
        }
    }

    private Path findBackupFile(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files.filter(p -> p.getFileName().toString().contains(".bak-")).findFirst().orElse(null);
        }
    }

    private LegacyScreenAnalysis analysisFor(
            String screenId, LegacyScreenRole role, String sourceRevisionToken) throws IOException {
        var jspEvidence = jspReader.read(
                "EgovEmployerList.jsp", Files.readString(BASELINE.resolve("EgovEmployerList.jsp")));
        var controllerEvidence = controllerReader.read(
                "EgovEmployerController.java", Files.readString(BASELINE.resolve("EgovEmployerController.java")));
        var voEvidence = voReader.read("EmployerVO.java", Files.readString(BASELINE.resolve("EmployerVO.java")));
        return new LegacyScreenAnalysis(
                screenId, role, jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("emp-project", sourceRevisionToken, Instant.now()),
                List.of(), Instant.now());
    }

    private void cleanup(ThymeleafConversionOperation operation) {
        jdbcTemplate.update(
                "DELETE FROM AI_THYMELEAF_CONVERSION_OPERATION WHERE OPERATION_ID = ?", operation.operationId());
        jdbcTemplate.update(
                "DELETE FROM AI_THYMELEAF_CONVERSION_OPERATION_IDEMPOTENCY WHERE OPERATION_ID = ?",
                operation.operationId());
    }

    private static Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                ThymeleafConversionOrchestrationServiceTest.class.getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }
}
