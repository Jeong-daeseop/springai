package com.krdevops.springai.config.mcp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * ARCH-0105, ARCH-0106: MCP transport 경계에서 상수시간으로 공유 토큰을 검증하고
 * 결과를 {@link McpActorContext}에 남긴다. 이 Filter는 요청을 거부하지 않는다 —
 * 실제 허용/차단 판단은 Tool 호출 시점의 {@link ToolAuthorizationPolicy}가 위험
 * 등급과 {@link McpAuthMode}를 함께 고려해 내린다.
 */
public class McpAuthenticationInterceptor extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-MCP-Token";

    private static final String CORRELATION_HEADER = "X-Correlation-ID";

    private final McpCredentialValidator credentialValidator;
    private final McpSecurityAuditLogger auditLogger;

    public McpAuthenticationInterceptor(
            McpCredentialValidator credentialValidator,
            McpSecurityAuditLogger auditLogger) {
        this.credentialValidator = credentialValidator;
        this.auditLogger = auditLogger;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isTransportPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String correlationId = resolveCorrelationId(request);
        McpCredentialValidator.Result result = credentialValidator.validate(request.getHeader(TOKEN_HEADER));
        response.setHeader(CORRELATION_HEADER, correlationId);
        auditLogger.authentication(correlationId, result.status(), request.getRemoteAddr());
        try {
            if (result.authenticated()) {
                Set<String> authorities = Arrays.stream(McpToolRiskLevel.values())
                        .map(level -> "MCP_" + level.name())
                        .collect(Collectors.toUnmodifiableSet());
                McpPrincipal principal = new McpPrincipal(
                        "mcp-shared-token", authorities, correlationId, result.credentialVersion());
                var grantedAuthorities = authorities.stream().map(SimpleGrantedAuthority::new).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, grantedAuthorities));
                McpActorContext.set(principal.actorContext());
            } else {
                McpActorContext.set(new McpActorContext(
                        false, null, Set.of(), correlationId, null, failureCode(result.status())));
            }
            filterChain.doFilter(request, response);
        } finally {
            McpActorContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private boolean isTransportPath(String path) {
        return "/mcp".equals(path) || path.startsWith("/mcp/")
                || "/sse".equals(path) || path.startsWith("/sse/");
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String supplied = request.getHeader(CORRELATION_HEADER);
        if (supplied != null && supplied.matches("[A-Za-z0-9._:-]{1,128}")) {
            return supplied;
        }
        return UUID.randomUUID().toString();
    }

    private McpAuthenticationFailureCode failureCode(McpCredentialValidator.Status status) {
        return switch (status) {
            case MISSING -> McpAuthenticationFailureCode.MCP_TOKEN_MISSING;
            case EXPIRED_PREVIOUS -> McpAuthenticationFailureCode.MCP_TOKEN_EXPIRED;
            case NOT_CONFIGURED -> McpAuthenticationFailureCode.MCP_CREDENTIAL_NOT_CONFIGURED;
            default -> McpAuthenticationFailureCode.MCP_TOKEN_INVALID;
        };
    }
}
