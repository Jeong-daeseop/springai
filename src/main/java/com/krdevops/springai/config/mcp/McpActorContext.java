package com.krdevops.springai.config.mcp;

/**
 * ARCH-0107: MCP 요청 인증 결과. 요청(스레드) 범위로만 유효하며
 * {@link McpAuthenticationInterceptor}가 설정·해제를 책임진다.
 */
public record McpActorContext(boolean authenticated, String principal) {

    private static final ThreadLocal<McpActorContext> CURRENT = new ThreadLocal<>();

    public static final McpActorContext ANONYMOUS = new McpActorContext(false, null);

    static void set(McpActorContext context) {
        CURRENT.set(context);
    }

    static void clear() {
        CURRENT.remove();
    }

    /** 현재 스레드에 인증 컨텍스트가 없으면(HTTP 필터를 거치지 않은 호출 등) 미인증으로 취급한다. */
    public static McpActorContext current() {
        McpActorContext context = CURRENT.get();
        return context != null ? context : ANONYMOUS;
    }
}
