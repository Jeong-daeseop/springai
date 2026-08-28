package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.api.BuildBoardPromptUseCase;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BoardGenerationDispatchServiceTest {

    private final GenerateBoardProjectUseCase generateBoardProjectUseCase = mock(GenerateBoardProjectUseCase.class);
    private final BuildBoardPromptUseCase buildBoardPromptUseCase = mock(BuildBoardPromptUseCase.class);
    private final BoardGenerationDispatchService dispatchService =
            new BoardGenerationDispatchService(generateBoardProjectUseCase, buildBoardPromptUseCase);

    @Test
    void autoLlmProviderDelegatesOnlyToOrchestrationUseCase() {
        BoardGenerationCommand command = command(null);
        BoardOrchestrationResult orchestrated = new BoardOrchestrationResult(
                false, "com", "LETTNBBS", "Notice", "/tmp/out",
                List.of("EgovNoticeList.jsp"), List.of(), "OK", "history");
        when(generateBoardProjectUseCase.execute(command)).thenReturn(orchestrated);

        BoardToolResult result = dispatchService.execute(command);

        assertThat(result).isInstanceOf(BoardToolResult.Orchestrated.class);
        assertThat(((BoardToolResult.Orchestrated) result).result()).isEqualTo(orchestrated);
        verify(generateBoardProjectUseCase).execute(command);
        verify(buildBoardPromptUseCase, never()).execute(any());
    }

    @Test
    void claudeLlmProviderDelegatesOnlyToPromptUseCase() {
        BoardGenerationCommand command = command("claude");
        PromptGenerationResult prompted = new PromptGenerationResult("prompt text");
        when(buildBoardPromptUseCase.execute(command)).thenReturn(prompted);

        BoardToolResult result = dispatchService.execute(command);

        assertThat(result).isInstanceOf(BoardToolResult.Prompted.class);
        assertThat(((BoardToolResult.Prompted) result).result()).isEqualTo(prompted);
        verify(buildBoardPromptUseCase).execute(command);
        verify(generateBoardProjectUseCase, never()).execute(any());
    }

    private BoardGenerationCommand command(String llmProvider) {
        return new BoardGenerationCommand(
                "com", "Notice", "egovframework.let.notice", Path.of("/tmp/out"),
                "LETTNBBS", "LETTNBBSMASTER", null, null, null,
                "5.0", "jsp", null, null, null, null, llmProvider);
    }
}
