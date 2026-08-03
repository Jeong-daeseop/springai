package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.config.mcp.McpActorContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Thymeleaf MCP Workflow가 전역 permitAll MCP transport 안에서도 선행 인가되도록 한다. */
@Service
public class ThymeleafToolAuthorizationService {
    private final String sharedSecret;

    public ThymeleafToolAuthorizationService(
            @Value("${app.thymeleaf.mcp-shared-secret:${app.figma.mcp-shared-secret:}}") String sharedSecret) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public void authorize(String provided) {
        if (McpActorContext.current().authenticated()) return;
        if (sharedSecret.isBlank()) throw new SecurityException("Thymeleaf MCP Tool 인증이 설정되지 않았습니다.");
        if (!MessageDigest.isEqual(sharedSecret.getBytes(StandardCharsets.UTF_8),
                (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Thymeleaf MCP Tool 인증에 실패했습니다.");
        }
    }
}
