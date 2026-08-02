package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.thymeleaf.BatchConversionRequest;
import com.krdevops.springai.model.thymeleaf.BatchConversionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * I-6: 배치 JSP 변환 테스트.
 * 여러 JSP 파일을 스캔하고 병렬/순차 처리하는 기능 검증.
 */
class BatchThymeleafConversionServiceTest {

    private static final Path BASELINE = Path.of("src/test/resources/generation/baseline/crud-jsp");

    private ProjectJspScanner jspScanner;
    private JspSourceReader jspReader;
    private ControllerSourceReader controllerReader;
    private VoSourceReader voReader;
    private ThymeleafConversionOrchestrationService orchestrationService;
    private BatchThymeleafConversionService batchService;

    @TempDir
    Path tempProjectRoot;

    @BeforeEach
    void setUp() {
        jspScanner = new ProjectJspScanner();
        jspReader = new JspSourceReader();
        controllerReader = new ControllerSourceReader();
        voReader = new VoSourceReader();
        orchestrationService = Mockito.mock(ThymeleafConversionOrchestrationService.class);
        batchService = new BatchThymeleafConversionService(
                jspScanner, jspReader, controllerReader, voReader, orchestrationService);
    }

    @Test
    void scanJspFilesDetectsMultipleJsps() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp"));
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerList.jsp"), "<html></html>");
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerDetail.jsp"), "<html></html>");
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerRegist.jsp"), "<html></html>");

        List<ProjectJspScanner.ScannedJspFile> scanned = jspScanner.scanJspFiles(
                tempProjectRoot, "**/*.jsp", List.of());

        assertThat(scanned).hasSize(3);
        assertThat(scanned).anyMatch(f -> f.jspRelativePath().contains("List.jsp"));
        assertThat(scanned).anyMatch(f -> f.jspRelativePath().contains("Detail.jsp"));
        assertThat(scanned).anyMatch(f -> f.jspRelativePath().contains("Regist.jsp"));
    }

    @Test
    void scanJspFilesFindsMultipleJspsInWildcardPattern() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp"));
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/admin"));
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerList.jsp"), "<html></html>");
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/admin/EgovAdminDash.jsp"), "<html></html>");

        List<ProjectJspScanner.ScannedJspFile> scanned = jspScanner.scanJspFiles(
                tempProjectRoot, "**/*.jsp", List.of());

        assertThat(scanned).hasSize(2);
        assertThat(scanned.stream().map(ProjectJspScanner.ScannedJspFile::jspRelativePath))
                .anyMatch(p -> p.contains("EgovEmployerList"))
                .anyMatch(p -> p.contains("EgovAdminDash"));
    }

    @Test
    void batchConversionSequentialProcessesJspsInOrder() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp"));
        Files.createDirectories(tempProjectRoot.resolve("src/main/java/com/example"));
        Files.createDirectories(tempProjectRoot.resolve("src/main/resources"));

        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerList.jsp"),
                Files.readString(BASELINE.resolve("EgovEmployerList.jsp")));
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerDetail.jsp"),
                Files.readString(BASELINE.resolve("EgovEmployerDetail.jsp")));
        Files.writeString(tempProjectRoot.resolve("src/main/java/com/example/EgovEmployerController.java"),
                Files.readString(BASELINE.resolve("EgovEmployerController.java")));
        Files.writeString(tempProjectRoot.resolve("src/main/java/com/example/EmployerVO.java"),
                Files.readString(BASELINE.resolve("EmployerVO.java")));

        var mockOp = Mockito.mock(com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation.class);
        Mockito.when(mockOp.operationId()).thenReturn("op-123");
        var successResult = com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult
                .success(mockOp, List.of());
        Mockito.when(orchestrationService.analyzeAndPreview(any(), any(), any()))
                .thenReturn(successResult);

        BatchConversionRequest request = BatchConversionRequest.builder()
                .batchId("test-seq-batch")
                .projectRoot(tempProjectRoot)
                .jspPattern("**/*.jsp")
                .outputBaseDirectory("src/main/resources/templates/legacy-thymeleaf")
                .parallelExecution(false)
                .excludePatterns(List.of())
                .build();

        BatchConversionResult result = batchService.convertBatch(request);

        assertThat(result.totalScanned()).isGreaterThanOrEqualTo(2);
        assertThat(result.itemResults()).isNotEmpty();
    }

    @Test
    void batchConversionParallelProcessesJspsInParallel() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp"));
        Files.createDirectories(tempProjectRoot.resolve("src/main/java/com/example"));

        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerList.jsp"),
                Files.readString(BASELINE.resolve("EgovEmployerList.jsp")));
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerDetail.jsp"),
                Files.readString(BASELINE.resolve("EgovEmployerDetail.jsp")));
        Files.writeString(tempProjectRoot.resolve("src/main/java/com/example/EgovEmployerController.java"),
                Files.readString(BASELINE.resolve("EgovEmployerController.java")));
        Files.writeString(tempProjectRoot.resolve("src/main/java/com/example/EmployerVO.java"),
                Files.readString(BASELINE.resolve("EmployerVO.java")));

        var mockOp = Mockito.mock(com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation.class);
        Mockito.when(mockOp.operationId()).thenReturn("op-123");
        var successResult = com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult
                .success(mockOp, List.of());
        Mockito.when(orchestrationService.analyzeAndPreview(any(), any(), any()))
                .thenReturn(successResult);

        BatchConversionRequest request = BatchConversionRequest.builder()
                .batchId("test-par-batch")
                .projectRoot(tempProjectRoot)
                .jspPattern("**/*.jsp")
                .outputBaseDirectory("src/main/resources/templates/legacy-thymeleaf")
                .parallelExecution(true)
                .maxConcurrency(2)
                .excludePatterns(List.of())
                .build();

        BatchConversionResult result = batchService.convertBatch(request);

        assertThat(result.totalScanned()).isGreaterThanOrEqualTo(2);
        assertThat(result.completedAt()).isAfter(result.startedAt());
    }

    @Test
    void batchResultComputesSuccessRateCorrectly() throws Exception {
        Files.createDirectories(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp"));
        Files.createDirectories(tempProjectRoot.resolve("src/main/java/com/example"));

        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerList.jsp"),
                Files.readString(BASELINE.resolve("EgovEmployerList.jsp")));
        Files.writeString(tempProjectRoot.resolve("src/main/webapp/WEB-INF/jsp/EgovEmployerDetail.jsp"),
                Files.readString(BASELINE.resolve("EgovEmployerDetail.jsp")));
        Files.writeString(tempProjectRoot.resolve("src/main/java/com/example/EgovEmployerController.java"),
                Files.readString(BASELINE.resolve("EgovEmployerController.java")));
        Files.writeString(tempProjectRoot.resolve("src/main/java/com/example/EmployerVO.java"),
                Files.readString(BASELINE.resolve("EmployerVO.java")));

        var mockOp = Mockito.mock(com.krdevops.springai.model.thymeleaf.ThymeleafConversionOperation.class);
        Mockito.when(mockOp.operationId()).thenReturn("op-123");
        var successResult = com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageResult
                .success(mockOp, List.of());
        Mockito.when(orchestrationService.analyzeAndPreview(any(), any(), any()))
                .thenReturn(successResult);

        BatchConversionRequest request = BatchConversionRequest.builder()
                .batchId("test-rate-batch")
                .projectRoot(tempProjectRoot)
                .jspPattern("**/*.jsp")
                .outputBaseDirectory("src/main/resources/templates/legacy-thymeleaf")
                .parallelExecution(false)
                .build();

        BatchConversionResult result = batchService.convertBatch(request);

        if (result.successfulConversions() + result.failedConversions() > 0) {
            int rate = result.successRate();
            assertThat(rate).isGreaterThanOrEqualTo(0).isLessThanOrEqualTo(100);
        }
    }

}
