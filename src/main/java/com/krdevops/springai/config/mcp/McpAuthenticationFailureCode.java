package com.krdevops.springai.config.mcp;

/** 외부 응답과 감사 로그에서 함께 사용하는 MCP 인증 실패 코드. */
public enum McpAuthenticationFailureCode {
    MCP_AUTH_REQUIRED,
    MCP_TOKEN_MISSING,
    MCP_TOKEN_INVALID,
    MCP_TOKEN_EXPIRED,
    MCP_CREDENTIAL_NOT_CONFIGURED,
    MCP_TOOL_RISK_UNREGISTERED
}
