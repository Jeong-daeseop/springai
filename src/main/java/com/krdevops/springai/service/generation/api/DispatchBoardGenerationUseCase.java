package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.board.BoardGenerationCommand;
import com.krdevops.springai.service.generation.board.BoardToolResult;

/** llmProvider(auto vs claude/기타)에 따라 {@link GenerateBoardProjectUseCase}/{@link BuildBoardPromptUseCase}로 분기한다. */
public interface DispatchBoardGenerationUseCase {

    BoardToolResult execute(BoardGenerationCommand command);
}
