package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MasterDetailServletScanProcessorTest {

    @Test
    void missingServletContext_isSkippedWithoutFailure() throws Exception {
        Path output = Files.createTempDirectory("master-detail-processor-test");
        try {
            CodeService codeService = mock(CodeService.class);
            var processor = new MasterDetailServletScanProcessor(codeService);
            GenerationContext generationContext = new GenerationContext("master-detail", "com", "MASTER", "Order",
                    "egovframework.let.order", output.toString(), "5.0", "jsp", Map.of());

            var result = processor.process(new GenerationProcessingContext(generationContext, null, null, null));

            assertThat(result.success()).isTrue();
            verifyNoInteractions(codeService);
        } finally {
            Files.deleteIfExists(output);
        }
    }
}
