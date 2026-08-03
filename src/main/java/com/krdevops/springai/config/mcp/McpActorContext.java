package com.krdevops.springai.config.mcp;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

/**
 * ARCH-0107: MCP 요청 인증 결과. 요청(스레드) 범위로만 유효하며
 * {@link McpAuthenticationInterceptor}가 설정·해제를 책임진다.
 */
public record McpActorContext(
        boolean authenticated,
        String principal,
        Set<String> authorities,
        String correlationId,
        String credentialVersion,
        McpAuthenticationFailureCode failureCode
) {

    private static final ThreadLocal<McpActorContext> CURRENT = new ThreadLocal<>();

    public static final McpActorContext ANONYMOUS =
            new McpActorContext(false, null, Set.of(), null, null, McpAuthenticationFailureCode.MCP_AUTH_REQUIRED);

    public McpActorContext {
        authorities = authorities == null ? Set.of() : Set.copyOf(authorities);
    }

    /** 기존 호출부 호환용 생성자. credential 원문은 어떤 경우에도 저장하지 않는다. */
    public McpActorContext(boolean authenticated, String principal) {
        this(authenticated, principal, Set.of(), null, null,
                authenticated ? null : McpAuthenticationFailureCode.MCP_AUTH_REQUIRED);
    }

    static void set(McpActorContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** 현재 스레드에 인증 컨텍스트가 없으면(HTTP 필터를 거치지 않은 호출 등) 미인증으로 취급한다. */
    public static McpActorContext current() {
        McpActorContext context = CURRENT.get();
        if (context != null) {
            return context;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof McpPrincipal principal) {
            return principal.actorContext();
        }
        return ANONYMOUS;
    }
}
