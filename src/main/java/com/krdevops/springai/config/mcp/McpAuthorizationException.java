package com.krdevops.springai.config.mcp;

/**
 * ARCH-0109: MCP Tool 인증 실패 표준 예외. 메시지에는 secret/token 값을 포함하지 않는다.
 */
public class McpAuthorizationException extends SecurityException {
    public McpAuthorizationException(String message) {
        super(message);
    }
}
