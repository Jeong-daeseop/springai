package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;

/**
 * {@link MasterDetailGenerationDispatchService}의 결과 — auto 경로(결정론적 오케스트레이션 결과)인지
 * claude 경로(Prompt 문자열 결과)인지 구분한다. {@link com.krdevops.springai.service.generation.mcp.MasterDetailGenerationMcpFacade}가
 * 이 타입으로 올바른 Formatter를 선택한다.
 */
public sealed interface MasterDetailToolResult {

    record Orchestrated(MasterDetailOrchestrationResult result) implements MasterDetailToolResult {}

    record Prompted(PromptGenerationResult result) implements MasterDetailToolResult {}
}
