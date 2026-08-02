package com.krdevops.springai.config.mcp;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * ARCH-0108: deny-by-default 인가 정책.
 * 인증된 호출은 위험 등급과 무관하게 항상 허용한다. 미인증 호출은 모드에 따라
 * AUDIT_ONLY(허용+감사 로그) 또는 REQUIRED(차단)로 처리한다.
 */
@Component
@RequiredArgsConstructor
public class ToolAuthorizationPolicy {

    private static final Logger log = LoggerFactory.getLogger(ToolAuthorizationPolicy.class);

    private final McpSecurityProperties properties;

    /**
     * @throws McpAuthorizationException REQUIRED 모드에서 미인증 호출일 때
     */
    public void authorize(String toolName, McpToolRiskLevel riskLevel) {
        McpActorContext actor = McpActorContext.current();

        if (actor.authenticated()) {
            return;
        }

        if (properties.getAuthMode() == McpAuthMode.AUDIT_ONLY) {
            log.warn("mcp_tool_auth_audit tool={} riskLevel={} authenticated=false mode=AUDIT_ONLY", toolName, riskLevel);
            return;
        }

        log.warn("mcp_tool_auth_denied tool={} riskLevel={} authenticated=false mode=REQUIRED", toolName, riskLevel);
        throw new McpAuthorizationException("MCP Tool 인증에 실패했습니다: " + toolName);
    }
}
