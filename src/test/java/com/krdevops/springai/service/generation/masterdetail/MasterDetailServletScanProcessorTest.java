package com.krdevops.springai.service.generation.masterdetail;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.EgovProperties;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.contract.OperationHashFactory;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import com.krdevops.springai.service.write.FileSystemApprovedProjectWritePort;
import com.krdevops.springai.service.write.SafePathResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * WP7 3차 pass 잔여 항목: {@code codeService.saveGeneratedCode} 직접 호출을 공용
 * {@code ApprovedProjectWritePort}(BEST_EFFORT_COMPATIBILITY)로 전환한다.
 */
class MasterDetailServletScanProcessorTest {

    @Test
    void missingServletContext_isSkippedWithoutFailure(@TempDir Path output) {
        CodeService codeService = mock(CodeService.class);
        ApprovedProjectWritePort writePort = mock(ApprovedProjectWritePort.class);
        var processor = new MasterDetailServletScanProcessor(codeService, writePort);
        GenerationContext generationContext = new GenerationContext("master-detail", "com", "MASTER", "Order",
                "egovframework.let.order", output.toString(), "5.0", "jsp", Map.of());

        var result = processor.process(new GenerationProcessingContext(generationContext, null, null, null));

        assertThat(result.success()).isTrue();
        verifyNoInteractions(codeService);
        verifyNoInteractions(writePort);
    }

    @Test
    void existingServletContext_patchesComponentScanThroughPort(@TempDir Path output) throws Exception {
        Path servletContext = servletContextXml(output, "base-package=\"egovframework.let.order\"");
        var processor = processor(output);
        GenerationContext generationContext = context(output);

        var result = processor.process(new GenerationProcessingContext(generationContext, null, null, null));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(servletContext))
                .contains("base-package=\"egovframework.let.order,egovframework.com\"");
    }

    @Test
    void alreadyPatched_isIdempotentWithoutWriting(@TempDir Path output) throws Exception {
        Path servletContext = servletContextXml(
                output, "base-package=\"egovframework.let.order,egovframework.com\"");
        String before = Files.readString(servletContext);
        var processor = processor(output);
        GenerationContext generationContext = context(output);

        var result = processor.process(new GenerationProcessingContext(generationContext, null, null, null));

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(servletContext)).isEqualTo(before);
    }

    @Test
    void patternNotFound_returnsFailureWithoutWriting(@TempDir Path output) throws Exception {
        Path servletContext = servletContextXml(output, "no-base-package-here");
        var processor = processor(output);
        GenerationContext generationContext = context(output);

        var result = processor.process(new GenerationProcessingContext(generationContext, null, null, null));

        assertThat(result.success()).isFalse();
        assertThat(result.failures().get(0).description()).contains("component-scan base-package 패턴을 찾을 수 없습니다");
    }

    @Test
    void writeFailure_reportsFailureThroughProcessorResult(@TempDir Path output) throws Exception {
        Path servletContext = servletContextXml(output, "base-package=\"egovframework.let.order\"");
        // 대상 파일 자체를 쓰기 금지로 만들어 BEST_EFFORT_COMPATIBILITY의 writeString이 실패하게 한다.
        boolean readOnlySet = servletContext.toFile().setWritable(false);
        org.junit.jupiter.api.Assumptions.assumeTrue(
                readOnlySet && !Files.isWritable(servletContext),
                "이 실행 환경(예: root)에서는 파일 쓰기 금지가 걸리지 않아 이 테스트를 건너뛴다.");
        try {
            var processor = processor(output);
            GenerationContext generationContext = context(output);

            var result = processor.process(new GenerationProcessingContext(generationContext, null, null, null));

            assertThat(result.success()).isFalse();
            assertThat(result.failures().get(0).description()).contains("servlet-context.xml");
        } finally {
            servletContext.toFile().setWritable(true);
        }
    }

    private Path servletContextXml(Path output, String basePackageAttr) throws Exception {
        Path path = output.resolve("src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        Files.createDirectories(path.getParent());
        Files.writeString(path, "<beans><context:component-scan " + basePackageAttr + "/></beans>");
        return path;
    }

    private GenerationContext context(Path output) {
        return new GenerationContext("master-detail", "com", "MASTER", "Order",
                "egovframework.let.order", output.toString(), "5.0", "jsp", Map.of());
    }

    private MasterDetailServletScanProcessor processor(Path outputRoot) {
        EgovProperties properties = new EgovProperties();
        EgovProperties.Output output = new EgovProperties.Output();
        output.setBasePath(outputRoot.toString());
        properties.setOutput(output);
        CodeService codeService = new CodeService(properties);
        FileSystemApprovedProjectWritePort writePort = new FileSystemApprovedProjectWritePort(
                new SafePathResolver(), new OperationHashFactory(new ObjectMapper()));
        return new MasterDetailServletScanProcessor(codeService, writePort);
    }
}
