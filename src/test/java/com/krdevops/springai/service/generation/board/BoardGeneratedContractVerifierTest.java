package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BoardGeneratedContractVerifierTest {

    @Test
    void missingRequiredLayer_returnsContractFailures() {
        GenerationContext generationContext = new GenerationContext(
                "board", "com", "LETTNBBS", "Notice", "egovframework.let.notice",
                "/tmp/out", "5.0", CrudViewType.JSP.value(), Map.of());
        GenerationExecution execution = mock(GenerationExecution.class);
        when(execution.succeededNames()).thenReturn(List.of("NoticeVO.java"));
        GenerationProcessingContext context = new GenerationProcessingContext(
                generationContext, mock(GenerationBlueprint.class), mock(RenderedGenerationPlan.class), execution);

        var result = new BoardGeneratedContractVerifier().verify(context);

        assertThat(result.failures()).isNotEmpty();
        assertThat(result.summaryFragment()).contains("누락 파일");
    }
}
