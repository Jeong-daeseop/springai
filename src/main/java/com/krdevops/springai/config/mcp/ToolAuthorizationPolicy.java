package com.krdevops.springai.config.mcp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * ARCH-0108: deny-by-default 인가 정책.
 * 인증된 호출은 위험 등급과 무관하게 항상 허용한다. 미인증 호출은 모드에 따라
 * AUDIT_ONLY(허용+감사 로그) 또는 REQUIRED(차단)로 처리한다.
 */
@Component
public class ToolAuthorizationPolicy {

    private final McpSecurityProperties properties;
    private final McpSecurityAuditLogger auditLogger;
    private final LegacyMcpCredentialValidator legacyCredentialValidator;

    @Autowired
    public ToolAuthorizationPolicy(McpSecurityProperties properties,
                                   McpSecurityAuditLogger auditLogger,
                                   LegacyMcpCredentialValidator legacyCredentialValidator) {
        this.properties = properties;
        this.auditLogger = auditLogger;
        this.legacyCredentialValidator = legacyCredentialValidator;
    }

    ToolAuthorizationPolicy(McpSecurityProperties properties) {
        this(properties, new McpSecurityAuditLogger(), LegacyMcpCredentialValidator.disabled());
    }

    /**
     * @throws McpAuthorizationException REQUIRED 모드에서 미인증 호출일 때
     */
    public void authorize(String toolName, McpToolRiskLevel riskLevel) {
        authorize(toolName, riskLevel, null);
    }

    public void authorize(String toolName, McpToolRiskLevel riskLevel, String toolInput) {
        McpActorContext actor = McpActorContext.current();

        if (actor.authenticated()) {
            if (!actor.authorities().contains("MCP_" + riskLevel.name()) && !actor.authorities().isEmpty()) {
                auditLogger.authorization(actor.correlationId(), toolName, riskLevel, "DENY_AUTHORITY", actor.credentialVersion());
                throw new McpAuthorizationException(McpAuthenticationFailureCode.MCP_AUTH_REQUIRED, toolName);
            }
            auditLogger.authorization(actor.correlationId(), toolName, riskLevel, "ALLOW", actor.credentialVersion());
            return;
        }

        if (properties.getAuthMode() == McpAuthMode.AUDIT_ONLY) {
            auditLogger.authorization(actor.correlationId(), toolName, riskLevel, "AUDIT_ALLOW", null);
            return;
        }

        if (properties.getAuthMode() == McpAuthMode.COMPATIBILITY
                && legacyCredentialValidator.isValid(toolInput)) {
            auditLogger.authorization(actor.correlationId(), toolName, riskLevel, "ALLOW_LEGACY", "legacy");
            return;
        }

        auditLogger.authorization(actor.correlationId(), toolName, riskLevel, "DENY", null);
        McpAuthenticationFailureCode failureCode = actor.failureCode() == null
                ? McpAuthenticationFailureCode.MCP_AUTH_REQUIRED : actor.failureCode();
        throw new McpAuthorizationException(failureCode, toolName);
    }
}
