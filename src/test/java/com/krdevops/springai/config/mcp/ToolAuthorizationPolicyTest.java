package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ARCH-0117: 위험 등급별 허용·거부 matrix 검증. */
class ToolAuthorizationPolicyTest {

    @AfterEach
    void clearActor() {
        McpActorContext.clear();
    }

    private ToolAuthorizationPolicy policy(McpAuthMode mode) {
        McpSecurityProperties properties = new McpSecurityProperties();
        properties.setAuthMode(mode);
        return new ToolAuthorizationPolicy(properties);
    }

    @ParameterizedTest
    @EnumSource(McpToolRiskLevel.class)
    void requiredMode_deniesUnauthenticated_forEveryRiskLevel(McpToolRiskLevel riskLevel) {
        McpActorContext.set(McpActorContext.ANONYMOUS);
        assertThatThrownBy(() -> policy(McpAuthMode.REQUIRED).authorize("anyTool", riskLevel))
                .isInstanceOf(McpAuthorizationException.class);
    }

    @ParameterizedTest
    @EnumSource(McpToolRiskLevel.class)
    void requiredMode_allowsAuthenticated_forEveryRiskLevel(McpToolRiskLevel riskLevel) {
        McpActorContext.set(new McpActorContext(true, "test-actor"));
        assertThatCode(() -> policy(McpAuthMode.REQUIRED).authorize("anyTool", riskLevel))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(McpToolRiskLevel.class)
    void auditOnlyMode_allowsUnauthenticated_butDoesNotThrow(McpToolRiskLevel riskLevel) {
        McpActorContext.set(McpActorContext.ANONYMOUS);
        assertThatCode(() -> policy(McpAuthMode.AUDIT_ONLY).authorize("anyTool", riskLevel))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(McpToolRiskLevel.class)
    void auditOnlyMode_allowsAuthenticated(McpToolRiskLevel riskLevel) {
        McpActorContext.set(new McpActorContext(true, "test-actor"));
        assertThatCode(() -> policy(McpAuthMode.AUDIT_ONLY).authorize("anyTool", riskLevel))
                .doesNotThrowAnyException();
    }

    @org.junit.jupiter.api.Test
    void noActorContextSet_isTreatedAsAnonymous() {
        // McpActorContext.current()는 set()이 호출되지 않았을 때 ANONYMOUS를 반환해야 한다
        // (예: HTTP Filter를 거치지 않은 직접 호출 경로).
        assertThatThrownBy(() -> policy(McpAuthMode.REQUIRED).authorize("anyTool", McpToolRiskLevel.READ))
                .isInstanceOf(McpAuthorizationException.class);
    }
}
