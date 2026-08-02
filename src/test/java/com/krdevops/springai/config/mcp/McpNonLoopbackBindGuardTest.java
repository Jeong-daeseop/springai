package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ARCH-0110: 비-loopback 기동 guard. */
class McpNonLoopbackBindGuardTest {

    @Test
    void loopbackAddress_withoutToken_startsUp() {
        McpSecurityProperties properties = new McpSecurityProperties();
        assertThatCode(() -> new McpNonLoopbackBindGuard("127.0.0.1", properties))
                .doesNotThrowAnyException();
    }

    @Test
    void nonLoopbackAddress_withoutToken_blocksStartup() {
        McpSecurityProperties properties = new McpSecurityProperties();
        assertThatThrownBy(() -> new McpNonLoopbackBindGuard("0.0.0.0", properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared-token");
    }

    @Test
    void nonLoopbackAddress_withToken_startsUp() {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setSharedToken("configured-token");
        assertThatCode(() -> new McpNonLoopbackBindGuard("0.0.0.0", properties))
                .doesNotThrowAnyException();
    }
}
