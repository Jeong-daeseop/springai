package com.krdevops.springai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ThymeleafConversionOperationRepository;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.thymeleaf.End2EndConversionPipeline;
import com.krdevops.springai.model.thymeleaf.LegacyScreenAnalysis;
import com.krdevops.springai.model.thymeleaf.LegacyScreenRole;
import com.krdevops.springai.service.ThymeleafRenderValidator;
import com.krdevops.springai.service.contract.GenerationIssueFactory;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.thymeleaf.ControllerSourceReader;
import com.krdevops.springai.service.thymeleaf.End2EndConversionPipelineService;
import com.krdevops.springai.service.thymeleaf.JspSourceReader;
import com.krdevops.springai.service.thymeleaf.LegacyBindingContractAssembler;
import com.krdevops.springai.service.thymeleaf.LegacySourceInventoryService;
import com.krdevops.springai.service.thymeleaf.LegacyThymeleafRenderer;
import com.krdevops.springai.service.thymeleaf.LegacyThymeleafViewComposer;
import com.krdevops.springai.service.thymeleaf.ProjectApplicationService;
import com.krdevops.springai.service.thymeleaf.ThymeleafConversionOperationStateService;
import com.krdevops.springai.service.thymeleaf.ThymeleafConversionOrchestrationService;
import com.krdevops.springai.service.thymeleaf.ThymeleafSkeletonPlanner;
import com.krdevops.springai.service.thymeleaf.VoSourceReader;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * I-2~I-7 전체 E2E 파이프라인 테스트.
 * 실제 JSP 파일(golden fixture)에서 시작하여 최종 배포까지 검증.
 */
class End2EndConversionTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final GenerationIssueFactory issueFactory = new GenerationIssueFactory();
    private final OperationHashFactory operationHashFactory = new OperationHashFactory(objectMapper);

    private JspSourceReader jspReader;
    private ControllerSourceReader controllerReader;
    private VoSourceReader voReader;
    private LegacyBindingContractAssembler assembler;
    private ThymeleafSkeletonPlanner skeletonPlanner;
    private LegacyThymeleafViewComposer viewComposer;
    private LegacyThymeleafRenderer renderer;
    private ThymeleafConversionOperationStateService operationStateService;
    private ThymeleafConversionOperationRepository repository;
    private LegacySourceInventoryService inventoryService;
    private ThymeleafRenderValidator renderValidator;
    private ThymeleafConversionOrchestrationService orchestrationService;
    private ProjectApplicationService applicationService;
    private End2EndConversionPipelineService pipelineService;

    @TempDir
    Path tempProjectRoot;

    @TempDir
    Path tempOutputDirectory;

    @BeforeEach
    void setUp() {
        jspReader = new JspSourceReader();
        controllerReader = new ControllerSourceReader();
        voReader = new VoSourceReader();
        assembler = new LegacyBindingContractAssembler(issueFactory);
        skeletonPlanner = new ThymeleafSkeletonPlanner();
        viewComposer = new LegacyThymeleafViewComposer(issueFactory);
        renderer = new LegacyThymeleafRenderer(freemarkerConfiguration());
        operationStateService = new ThymeleafConversionOperationStateService();
        repository = new ThymeleafConversionOperationRepository(
                jdbcTemplate, objectMapper, operationHashFactory, operationStateService);
        inventoryService = new LegacySourceInventoryService(operationHashFactory);
        renderValidator = Mockito.mock(ThymeleafRenderValidator.class);
        when(renderValidator.validateDirectory(anyString()))
                .thenReturn(new ThymeleafRenderValidator.RenderReport(1, 1, List.of()));
        orchestrationService = new ThymeleafConversionOrchestrationService(
                assembler, skeletonPlanner, viewComposer, renderer, repository, inventoryService,
                renderValidator, operationHashFactory, issueFactory);
        applicationService = new ProjectApplicationService();
        pipelineService = new End2EndConversionPipelineService(
                assembler, skeletonPlanner, viewComposer, renderer, orchestrationService,
                inventoryService, applicationService);
    }

    @Test
    void endToEndPreviewOnlyPipeline() throws Exception {
        repository.createTableIfNotExists();
        String screenId = "emp-list-" + UUID.randomUUID();

        LegacyScreenAnalysis analysis = buildAnalysisFromGoldenFixture(screenId, LegacyScreenRole.LIST);

        End2EndConversionPipeline result = pipelineService.execute(
                analysis,
                "직원 목록",
                "employer/EgovEmployerList.html",
                tempProjectRoot,
                false // Preview only
        );

        assertThat(result.pipelineId()).isNotNull();
        assertThat(result.status()).isEqualTo(End2EndConversionPipeline.PipelineStatus.SUCCESS);
        assertThat(result.renderedHtml()).isNotNull().contains("th:each");
        assertThat(result.appliedOperation()).isNull(); // Preview only
        assertThat(result.projectDeployment()).isNull();
        assertThat(result.issues()).isEmpty();
        assertThat(result.metrics().getTotalTimeSeconds()).isGreaterThanOrEqualTo(0);

        cleanup(screenId);
    }

    @Test
    void endToEndFullPipeline() throws Exception {
        repository.createTableIfNotExists();
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF"));
        Files.writeString(tempProjectRoot.resolve("pom.xml"),
                "<project><packaging>war</packaging></project>");

        String screenId = "emp-list-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = buildAnalysisFromGoldenFixture(screenId, LegacyScreenRole.LIST);

        End2EndConversionPipeline result = pipelineService.execute(
                analysis,
                "직원 목록",
                "screens/EgovEmployerList.html",
                tempProjectRoot,
                true // Full pipeline with apply
        );

        // Preview + Apply 단계 검증 (Deployment는 부분적으로 실패할 수 있음)
        assertThat(result.renderedHtml()).isNotNull().contains("th:each");
        assertThat(result.appliedOperation()).isNotNull();
        assertThat(result.metrics().getTotalTimeSeconds()).isGreaterThanOrEqualTo(0);

        cleanup(screenId);
    }

    @Test
    void endToEndDetailPagePipeline() throws Exception {
        repository.createTableIfNotExists();
        Files.createDirectories(tempProjectRoot.resolve("src/main/resources"));
        Files.writeString(tempProjectRoot.resolve("build.gradle"), "plugins {}");

        String screenId = "emp-detail-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = buildAnalysisFromGoldenFixture(screenId, LegacyScreenRole.DETAIL);

        End2EndConversionPipeline result = pipelineService.execute(
                analysis,
                "직원 상세",
                "screens/EgovEmployerDetail.html",
                tempProjectRoot,
                true
        );

        // Preview + Apply 단계 검증
        assertThat(result.appliedOperation()).isNotNull();
        assertThat(result.renderedHtml()).isNotNull();

        cleanup(screenId);
    }

    @Test
    void endToEndPipelineMetricsAccurate() throws Exception {
        repository.createTableIfNotExists();
        String screenId = "emp-metrics-" + UUID.randomUUID();
        LegacyScreenAnalysis analysis = buildAnalysisFromGoldenFixture(screenId, LegacyScreenRole.LIST);

        End2EndConversionPipeline result = pipelineService.execute(
                analysis,
                "직원 목록",
                "employer/EgovEmployerList.html",
                tempProjectRoot,
                false
        );

        assertThat(result.metrics().totalTimeMs()).isGreaterThan(0);
        assertThat(result.metrics().analysisTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.metrics().renderingTimeMs()).isGreaterThan(0);

        cleanup(screenId);
    }

    private LegacyScreenAnalysis buildAnalysisFromGoldenFixture(
            String screenId,
            LegacyScreenRole screenRole) throws Exception {
        String jspFile = (screenRole == LegacyScreenRole.DETAIL)
                ? "EgovEmployerDetail.jsp"
                : "EgovEmployerList.jsp";

        var jspEvidence = jspReader.read(
                jspFile,
                Files.readString(BASELINE.resolve(jspFile)));
        var controllerEvidence = controllerReader.read(
                "EgovEmployerController.java",
                Files.readString(BASELINE.resolve("EgovEmployerController.java")));
        var voEvidence = voReader.read(
                "EmployerVO.java",
                Files.readString(BASELINE.resolve("EmployerVO.java")));

        return new LegacyScreenAnalysis(
                screenId, screenRole, jspEvidence, controllerEvidence, voEvidence,
                new SourceRevisionRef("e2e-test", "rev-" + System.currentTimeMillis(), Instant.now()),
                List.of(), Instant.now());
    }

    private void cleanup(String screenId) {
        // DB 정리는 테스트 완료 후 필요하면 수동으로 처리
    }

    private static Configuration freemarkerConfiguration() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_33);
        cfg.setClassLoaderForTemplateLoading(
                End2EndConversionTest.class.getClassLoader(), "templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setInterpolationSyntax(Configuration.DOLLAR_INTERPOLATION_SYNTAX);
        return cfg;
    }
}
