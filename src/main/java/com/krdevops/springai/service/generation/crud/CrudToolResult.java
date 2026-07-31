package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;

/**
 * {@link CrudGenerationDispatchService}의 결과 — auto 경로(결정론적 오케스트레이션 결과)인지
 * claude 경로(Prompt 문자열 결과)인지 구분한다. {@link com.krdevops.springai.service.generation.mcp.CrudGenerationMcpFacade}가
 * 이 타입으로 올바른 Formatter를 선택한다.
 */
public sealed interface CrudToolResult {

    record Orchestrated(CrudOrchestrationResult result) implements CrudToolResult {}

    record Prompted(PromptGenerationResult result) implements CrudToolResult {}
}
