package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MasterDetailMainControllerProcessorTest {

    @Test
    void listNotSaved_skipsEntryPointUpdate() {
        CodeService codeService = mock(CodeService.class);
        var processor = new MasterDetailMainControllerProcessor(codeService);
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
    }
}
