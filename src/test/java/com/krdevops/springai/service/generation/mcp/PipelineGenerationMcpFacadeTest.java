package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.api.BuildBoardPromptUseCase;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import com.krdevops.springai.service.generation.board.BoardGenerationDispatchService;
import com.krdevops.springai.service.generation.board.BoardToolResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PipelineGenerationMcpFacadeTest {

    @Test
    void boardFacade_delegatesOnlyToFeatureUseCase() {
        GenerateBoardProjectUseCase useCase = mock(GenerateBoardProjectUseCase.class);
        BoardGenerationResultFormatter formatter = mock(BoardGenerationResultFormatter.class);
        BoardOrchestrationResult result = new BoardOrchestrationResult(
                false, "com", "LETTNBBS", "Notice", "/tmp/out",
                java.util.List.of("EgovNoticeList.jsp"), java.util.List.of(), "OK", "history");
        BoardToolResult toolResult = new BoardToolResult.Orchestrated(result);
        when(useCase.execute(any())).thenReturn(result);
        when(formatter.format(toolResult)).thenReturn("formatted");

        BoardGenerationDispatchService dispatchService = new BoardGenerationDispatchService(
                useCase, mock(BuildBoardPromptUseCase.class));
        BoardGenerationMcpFacade facade = new BoardGenerationMcpFacade(dispatchService, formatter);
        String response = facade.buildBoardFeature(
                "com", "Notice", "egovframework.let.notice", "/tmp/out", null,
                null, null, null, null, null, "5.0", "jsp",
                null, null, null, null, null, null, null, null, null, null);

        assertThat(response).isEqualTo("formatted");
        verify(useCase).execute(any());
        verify(formatter).format(toolResult);
    }
}
