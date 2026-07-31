package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.board.BoardGenerationCommand;

/** 게시판(BBS) 소스 생성 — llmProvider 분기가 없어 항상 결정론적 오케스트레이션으로 생성·저장한다. */
public interface GenerateBoardProjectUseCase {

    BoardOrchestrationResult execute(BoardGenerationCommand command);
}
