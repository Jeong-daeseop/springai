package com.krdevops.springai.config.mcp;

import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 인증정보 원문 없이 구조화 필드만 남기는 MCP 보안 감사 경계. */
@Component
public class McpSecurityAuditLogger {
    private static final Logger log = LoggerFactory.getLogger(McpSecurityAuditLogger.class);

    public void authentication(String correlationId, McpCredentialValidator.Status status, String remoteAddress) {
        try (var ignored = ObservabilityContextHolder.openEvent("mcp_authentication")) {
            if (status == McpCredentialValidator.Status.VALID_CURRENT
                    || status == McpCredentialValidator.Status.VALID_PREVIOUS) {
                log.info("mcp_authentication correlationId={} status={} remoteAddress={}",
                        correlationId, status, remoteAddress);
            } else {
                log.warn("mcp_authentication correlationId={} status={} remoteAddress={}",
                        correlationId, status, remoteAddress);
            }
        }
    }

    public void authorization(String correlationId, String toolName, McpToolRiskLevel risk,
                              String decision, String credentialVersion) {
        try (var ignored = ObservabilityContextHolder.openEvent("mcp_authorization")) {
            log.info("mcp_authorization correlationId={} tool={} riskLevel={} decision={} credentialVersion={}",
                    correlationId, toolName, risk, decision, credentialVersion);
        }
    }
}
