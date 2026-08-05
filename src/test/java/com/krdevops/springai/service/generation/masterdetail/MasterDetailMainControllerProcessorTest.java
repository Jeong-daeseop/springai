package com.krdevops.springai.service.generation.masterdetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * WP7 3차 pass 잔여 항목: {@code codeService.saveGeneratedCode} 직접 호출을 공용
 * {@code ApprovedProjectWritePort}(BEST_EFFORT_COMPATIBILITY)로 전환한다 — 단일 파일 write라
 * 배치 이점은 없지만 write 경로를 일원화한다.
 */
class MasterDetailMainControllerProcessorTest {

    @Test
    void listNotSaved_skipsEntryPointUpdate() {
        CodeService codeService = mock(CodeService.class);
        ApprovedProjectWritePort writePort = mock(ApprovedProjectWritePort.class);
        var processor = new MasterDetailMainControllerProcessor(codeService, writePort);
        MasterDetailTemplateModel model = mock(MasterDetailTemplateModel.class);
        when(model.domain()).thenReturn("Order");
        GenerationContext generationContext = new GenerationContext("master-detail", "com", "MASTER", "Order",
                "egovframework.let.order", "/tmp/out", "5.0", "jsp",
                Map.of("masterDetail.model", model));
        GenerationExecution execution = mock(GenerationExecution.class);
        when(execution.succeededNames()).thenReturn(List.of());

        var result = processor.process(new GenerationProcessingContext(
                generationContext, null, null, execution));

        assertThat(result.success()).isTrue();
        verifyNoInteractions(codeService);
        verifyNoInteractions(writePort);
    }

    @Test
    void listSaved_writesEntryPointControllerWithRedirectUrlThroughPort(@TempDir Path outputRoot) throws Exception {
        var processor = processor(outputRoot);
        MasterDetailTemplateModel model = mock(MasterDetailTemplateModel.class);
        when(model.domain()).thenReturn("Order");
        when(model.urlPrefix()).thenReturn("/mst/order");
        GenerationContext generationContext = new GenerationContext("master-detail", "com", "MASTER", "Order",
                "egovframework.let.order", outputRoot.toString(), "5.0", "jsp",
                Map.of("masterDetail.model", model));
        GenerationExecution execution = mock(GenerationExecution.class);
        when(execution.succeededNames()).thenReturn(List.of("EgovOrderList.jsp"));

        var result = processor.process(new GenerationProcessingContext(
                generationContext, null, null, execution));

        assertThat(result.success()).isTrue();
        Path controllerPath = outputRoot.resolve("src/main/java/egovframework/com/web/EgovMainController.java");
        assertThat(controllerPath).isRegularFile();
        assertThat(Files.readString(controllerPath))
                .contains("public class EgovMainController")
                .contains("return \"redirect:/mst/orderList.do\";");
    }

    @Test
    void listSaved_writeFailure_reportsFailureThroughProcessorResult(@TempDir Path outputRoot) throws Exception {
        // EgovMainController.java 자리를 디렉터리로 선점해 쓰기가 실패하게 한다.
        Path controllerPath = outputRoot.resolve("src/main/java/egovframework/com/web/EgovMainController.java");
        Files.createDirectories(controllerPath);

        var processor = processor(outputRoot);
        MasterDetailTemplateModel model = mock(MasterDetailTemplateModel.class);
        when(model.domain()).thenReturn("Order");
        when(model.urlPrefix()).thenReturn("/mst/order");
        GenerationContext generationContext = new GenerationContext("master-detail", "com", "MASTER", "Order",
                "egovframework.let.order", outputRoot.toString(), "5.0", "jsp",
                Map.of("masterDetail.model", model));
        GenerationExecution execution = mock(GenerationExecution.class);
        when(execution.succeededNames()).thenReturn(List.of("EgovOrderList.jsp"));

        var result = processor.process(new GenerationProcessingContext(
                generationContext, null, null, execution));

        assertThat(result.success()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0).description()).contains("EgovMainController.java");
    }

    private MasterDetailMainControllerProcessor processor(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new MasterDetailMainControllerProcessor(codeService, writePort);
    }
}
