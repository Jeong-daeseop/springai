package com.krdevops.springai.config.mcp;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * ARCH-0108: Tool 메서드 실행 전에 {@link ToolAuthorizationPolicy}를 통과시키는 데코레이터.
 * 인가 실패 시 위임 대상의 {@code call(...)}은 전혀 호출되지 않으므로 Repository·파일·외부
 * API 호출이 발생하지 않는다(완료 Gate: "인증 전에 Repository·파일·외부 API 호출 0건").
 */
public class McpAuthorizingToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final McpToolRiskLevel riskLevel;
    private final ToolAuthorizationPolicy policy;

    public McpAuthorizingToolCallback(ToolCallback delegate, McpToolRiskLevel riskLevel, ToolAuthorizationPolicy policy) {
        this.delegate = delegate;
        this.riskLevel = riskLevel;
        this.policy = policy;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        policy.authorize(delegate.getToolDefinition().name(), riskLevel);
        return delegate.call(toolInput);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        policy.authorize(delegate.getToolDefinition().name(), riskLevel);
        return delegate.call(toolInput, toolContext);
    }

    /** 원본 {@code MethodToolCallback} 접근용 — 리플렉션 기반 계약 테스트(예: MCP snapshot)에서 사용. */
    public ToolCallback delegate() {
        return delegate;
    }

    public McpToolRiskLevel riskLevel() {
        return riskLevel;
    }
}
