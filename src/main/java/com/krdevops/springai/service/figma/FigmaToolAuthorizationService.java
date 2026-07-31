package com.krdevops.springai.service.figma;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** `/mcp/**` 전역 permitAll을 유지하면서 신규 Figma Tool만 별도 공유 비밀키로 인가한다. */
@Service
public class FigmaToolAuthorizationService {

    private final String sharedSecret;

    public FigmaToolAuthorizationService(
            @Value("${app.figma.mcp-shared-secret:}") String sharedSecret
    ) {
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public void authorize(String providedSecret) {
        if (sharedSecret.isBlank()) {
            throw new SecurityException("Figma MCP Tool 인증이 설정되지 않았습니다.");
        }
        byte[] expected = sharedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (providedSecret == null ? "" : providedSecret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            throw new SecurityException("Figma MCP Tool 인증에 실패했습니다.");
        }
    }
}
