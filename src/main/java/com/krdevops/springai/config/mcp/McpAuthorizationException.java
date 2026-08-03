package com.krdevops.springai.config.mcp;

/**
 * ARCH-0109: MCP Tool 인증 실패 표준 예외. 메시지에는 secret/token 값을 포함하지 않는다.
 */
public class McpAuthorizationException extends SecurityException {
    private final McpAuthenticationFailureCode code;

    public McpAuthorizationException(McpAuthenticationFailureCode code, String toolName) {
        super(code.name() + ": MCP Tool 호출이 거부되었습니다. tool=" + toolName);
        this.code = code;
    }

    public McpAuthenticationFailureCode getCode() {
        return code;
    }
}
