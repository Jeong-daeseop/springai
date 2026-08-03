package com.krdevops.springai.config.mcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Instant;

/**
 * ARCH-0104: MCP 공통 credential 설정.
 * {@code app.mcp.shared-token}이 비어 있으면 REQUIRED 모드에서 모든 위험 Tool 호출이 거부된다
 * (fail-closed) — 반대로 AUDIT_ONLY 모드에서는 토큰 없이도 기존과 동일하게 동작한다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.mcp")
public class McpSecurityProperties {

    private String sharedToken = "";
    private String previousSharedToken = "";
    private Instant previousTokenValidUntil;
    private Instant legacyCredentialValidUntil;
    private McpAuthMode authMode = McpAuthMode.REQUIRED;

    public boolean hasSharedToken() {
        return sharedToken != null && !sharedToken.isBlank();
    }

    public boolean hasPreviousSharedToken() {
        return previousSharedToken != null && !previousSharedToken.isBlank();
    }
}
