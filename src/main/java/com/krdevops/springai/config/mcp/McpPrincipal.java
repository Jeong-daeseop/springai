package com.krdevops.springai.config.mcp;

import java.io.Serializable;
import java.util.Set;

/** credential 원문 없이 SecurityContext에 보관하는 MCP 인증 주체. */
public record McpPrincipal(
        String name,
        Set<String> authorities,
        String correlationId,
        String credentialVersion
) implements Serializable {

    public McpPrincipal {
        authorities = Set.copyOf(authorities);
    }

    public McpActorContext actorContext() {
        return new McpActorContext(true, name, authorities, correlationId, credentialVersion, null);
    }
}
