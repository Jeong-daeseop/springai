package com.krdevops.springai.config.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * ARCH-0105, ARCH-0106: MCP transport 경계에서 상수시간으로 공유 토큰을 검증하고
 * 결과를 {@link McpActorContext}에 남긴다. 이 Filter는 요청을 거부하지 않는다 —
 * 실제 허용/차단 판단은 Tool 호출 시점의 {@link ToolAuthorizationPolicy}가 위험
 * 등급과 {@link McpAuthMode}를 함께 고려해 내린다.
 */
public class McpAuthenticationInterceptor extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-MCP-Token";

    private final McpSecurityProperties properties;

    public McpAuthenticationInterceptor(McpSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/mcp/") && !path.startsWith("/sse/")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            McpActorContext.set(resolveActor(request));
            filterChain.doFilter(request, response);
        } finally {
            McpActorContext.clear();
        }
    }

    private McpActorContext resolveActor(HttpServletRequest request) {
        if (!properties.hasSharedToken()) {
            return McpActorContext.ANONYMOUS;
        }
        String provided = request.getHeader(TOKEN_HEADER);
        if (provided == null) {
            return McpActorContext.ANONYMOUS;
        }
        byte[] expectedBytes = properties.getSharedToken().getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        if (MessageDigest.isEqual(expectedBytes, providedBytes)) {
            return new McpActorContext(true, "mcp-shared-token");
        }
        return McpActorContext.ANONYMOUS;
    }
}
