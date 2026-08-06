package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ARCH-0118/ARCH-0119: 인증 실패 시 위임 대상({@code delegate.call(...)}) 호출이 0건임을
 * 검증한다 — Tool 메서드 본문의 Repository/파일/외부 API 호출은 이 delegate.call() 내부에서
 * 일어나므로, 이 호출이 발생하지 않으면 그 부작용도 발생하지 않는다.
 */
class McpAuthorizingToolCallbackTest {

    @AfterEach
    void clearActor() {
        McpActorContext.clear();
    }

    private ToolAuthorizationPolicy requiredPolicy() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setAuthMode(McpAuthMode.REQUIRED);
        return new ToolAuthorizationPolicy(properties);
    }

    @Test
    void deniedCall_neverInvokesDelegate() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = ToolDefinition.builder().name("riskyTool").description("d").inputSchema("{}").build();
        when(delegate.getToolDefinition()).thenReturn(definition);

        McpAuthorizingToolCallback wrapper =
                new McpAuthorizingToolCallback(delegate, McpToolRiskLevel.FILE_WRITE, requiredPolicy());

        McpActorContext.set(McpActorContext.ANONYMOUS);

        assertThatThrownBy(() -> wrapper.call("{}")).isInstanceOf(McpAuthorizationException.class);
        verify(delegate, never()).call(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void allowedCall_invokesDelegateAndReturnsResult() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = ToolDefinition.builder().name("safeTool").description("d").inputSchema("{}").build();
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.call("{}")).thenReturn("ok");

        McpAuthorizingToolCallback wrapper =
                new McpAuthorizingToolCallback(delegate, McpToolRiskLevel.READ, requiredPolicy());

        McpActorContext.set(new McpActorContext(true, "test-actor"));

        assertThat(wrapper.call("{}")).isEqualTo("ok");
        verify(delegate).call("{}");
    }

    @Test
    void toolDefinitionAndMetadata_alwaysDelegate_regardlessOfAuthentication() {
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = ToolDefinition.builder().name("anyTool").description("d").inputSchema("{}").build();
        when(delegate.getToolDefinition()).thenReturn(definition);

        McpAuthorizingToolCallback wrapper =
                new McpAuthorizingToolCallback(delegate, McpToolRiskLevel.APPLY, requiredPolicy());

        McpActorContext.set(McpActorContext.ANONYMOUS);

        assertThat(wrapper.getToolDefinition()).isEqualTo(definition);
    }

    @Test
    void tool_허용과_거부를_low_cardinality_metric으로_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);
        ToolCallback delegate = mock(ToolCallback.class);
        ToolDefinition definition = ToolDefinition.builder()
                .name("generateCrudSource").description("d").inputSchema("{}").build();
        when(delegate.getToolDefinition()).thenReturn(definition);
        when(delegate.call("{}")).thenReturn("ok");
        McpAuthorizingToolCallback wrapper = new McpAuthorizingToolCallback(
                delegate, McpToolRiskLevel.READ, requiredPolicy(), McpSensitiveDataRedactor.noop(), telemetry);

        McpActorContext.set(new McpActorContext(true, "test-actor"));
        assertThat(wrapper.call("{}")).isEqualTo("ok");
        McpActorContext.set(McpActorContext.ANONYMOUS);
        assertThatThrownBy(() -> wrapper.call("{}"))
                .isInstanceOf(McpAuthorizationException.class);

        assertThat(registry.get("springai.tool.calls.total")
                .tag("tool_family", "GENERATION").tag("outcome", "SUCCESS").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("springai.tool.calls.total")
                .tag("tool_family", "GENERATION").tag("outcome", "DENIED").counter().count())
                .isEqualTo(1);
    }
}
