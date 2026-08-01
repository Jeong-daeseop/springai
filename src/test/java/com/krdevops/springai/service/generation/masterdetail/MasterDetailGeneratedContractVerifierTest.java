package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MasterDetailGeneratedContractVerifierTest {

    @Test
    void missingRequiredLayer_returnsContractFailures() {
        GenerationContext generationContext = new GenerationContext(
                "master-detail", "com", "MASTER", "Order", "egovframework.let.order",
                "/tmp/out", "5.0", CrudViewType.JSP.value(), Map.of(
                        "masterDetail.model", mockModel()));
        GenerationExecution execution = mock(GenerationExecution.class);
        when(execution.succeededNames()).thenReturn(List.of("OrderVO.java"));
        GenerationProcessingContext context = new GenerationProcessingContext(
                generationContext, mock(com.krdevops.springai.service.generation.model.GenerationBlueprint.class),
                mock(RenderedGenerationPlan.class), execution);

        var result = new MasterDetailGeneratedContractVerifier().verify(context);

        assertThat(result.failures()).isNotEmpty();
        assertThat(result.summaryFragment()).contains("누락 파일");
    }

    private static com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel mockModel() {
        var model = mock(com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel.class);
        var detail = mock(com.krdevops.springai.model.crud.CrudTemplateModel.class);
        when(detail.domain()).thenReturn("OrderItem");
        when(model.detail()).thenReturn(detail);
        return model;
    }
}
