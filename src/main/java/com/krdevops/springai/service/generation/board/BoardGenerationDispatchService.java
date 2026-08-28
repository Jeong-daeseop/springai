package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.generation.api.BuildBoardPromptUseCase;
import com.krdevops.springai.service.generation.api.DispatchBoardGenerationUseCase;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider(auto vs claude/기타) 분기가 존재하는 유일한 지점. Tool/Facade는 이 분기 로직을
 * 갖지 않는다(CRUD의 {@code ORT-PRN-001}과 동일 원칙).
 */
@Service
@RequiredArgsConstructor
public class BoardGenerationDispatchService implements DispatchBoardGenerationUseCase {

    private final GenerateBoardProjectUseCase generateBoardProjectUseCase;
    private final BuildBoardPromptUseCase buildBoardPromptUseCase;

    @Override
    public BoardToolResult execute(BoardGenerationCommand command) {
        return command.isAuto()
                ? new BoardToolResult.Orchestrated(generateBoardProjectUseCase.execute(command))
                : new BoardToolResult.Prompted(buildBoardPromptUseCase.execute(command));
    }
}
