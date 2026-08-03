package com.krdevops.springai.config.mcp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * ARCH-0110: {@code server.address}가 loopback이 아닌데 공유 토큰이 없으면 기동을 차단한다.
 * `/mcp/**`가 여전히 Spring Security 상 permitAll이므로, non-loopback으로 노출되는 순간
 * 공유 토큰 없이는 REQUIRED 모드조차 무의미해지기 때문이다(외부에서 접근 가능한데 아무도
 * 인증할 방법이 없는 상태를 막는다).
 */
@Component
public class McpNonLoopbackBindGuard {

    public McpNonLoopbackBindGuard(
            @Value("${server.address:127.0.0.1}") String serverAddress,
            McpSecurityProperties properties) {
        if (!isLoopback(serverAddress)
                && (!properties.hasSharedToken() || properties.getAuthMode() != McpAuthMode.REQUIRED)) {
            throw new IllegalStateException(
                    "MCP 서버가 non-loopback 주소(" + serverAddress + ")에 바인딩되는데 "
                    + "공통 인증이 REQUIRED 상태로 설정되지 않았습니다. "
                    + "비-loopback 배포에서는 MCP_SHARED_TOKEN과 MCP_AUTH_MODE=REQUIRED가 필수입니다.");
        }
    }

    private boolean isLoopback(String address) {
        try {
            return InetAddress.getByName(address).isLoopbackAddress();
        } catch (UnknownHostException e) {
            // 주소를 확인할 수 없으면 안전하게 non-loopback으로 간주한다.
            return false;
        }
    }
}
