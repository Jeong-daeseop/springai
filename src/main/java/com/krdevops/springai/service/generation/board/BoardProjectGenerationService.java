package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 게시판 소스 생성 Use Case. 모든 실행은 Board Pipeline을 통해 수행한다.
 */
@Service
public class BoardProjectGenerationService implements GenerateBoardProjectUseCase {

    private final BoardGenerationPipelineService pipeline;
    private final BoardGenerationResultAssembler assembler;
    @Autowired
    public BoardProjectGenerationService(BoardGenerationPipelineService pipeline,
                                         BoardGenerationResultAssembler assembler) {
        this.pipeline = pipeline;
        this.assembler = assembler;
    }

    @Override
    public BoardOrchestrationResult execute(BoardGenerationCommand command) {
        BoardPipelineResult result = pipeline.execute(command);
        return assembler.assemble(command, result.plan(), result,
                result.validationSummary(), result.historySummary());
    }
}
