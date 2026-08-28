package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;

/**
 * {@link BoardGenerationDispatchService}의 결과 — auto 경로(결정론적 오케스트레이션 결과)인지
 * claude 경로(Prompt 문자열 결과)인지 구분한다. {@link com.krdevops.springai.service.generation.mcp.BoardGenerationResultFormatter}가
 * 이 타입으로 올바른 Formatter를 선택한다(CRUD의 {@code CrudToolResult}와 동일 패턴).
 */
public sealed interface BoardToolResult {

    record Orchestrated(BoardOrchestrationResult result) implements BoardToolResult {}

    record Prompted(PromptGenerationResult result) implements BoardToolResult {}
}
