package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.service.WarEntryPointConfigurer;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BoardEntryPointProcessorTest {

    @Test
    void listNotSaved_skipsEntryPointUpdate() {
        WarEntryPointConfigurer entryPoint = mock(WarEntryPointConfigurer.class);
        var processor = new BoardEntryPointProcessor(entryPoint);
        BoardTemplateModel model = mock(BoardTemplateModel.class);
        when(model.domain()).thenReturn("Notice");
        GenerationContext generationContext = new GenerationContext("board", "com", "BBS", "Notice",
                "egovframework.let.notice", "/tmp/out", "5.0", "jsp", Map.of(BoardGenerationAttributes.MODEL, model));
        GenerationExecution execution = mock(GenerationExecution.class);
        when(execution.succeededNames()).thenReturn(List.of());

        var result = processor.process(new GenerationProcessingContext(generationContext, null, null, execution));

        assertThat(result.success()).isTrue();
        verifyNoInteractions(entryPoint);
    }
}
