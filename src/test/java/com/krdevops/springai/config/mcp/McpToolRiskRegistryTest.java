package com.krdevops.springai.config.mcp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ARCH-0102/ARCH-0103: 위험 등급 inventory 커버리지.
 * 34개 전수 커버리지 자체는 {@code McpConfig.allToolCallbacks}의 fail-fast 검증과
 * {@code SpringaiApplicationTests}(컨텍스트 기동 성공)가 실질적으로 보증한다 — 여기서는
 * registry의 단독 동작(등록/미등록 판정)만 검증한다.
 */
class McpToolRiskRegistryTest {

    private final McpToolRiskRegistry registry = new McpToolRiskRegistry();

    @Test
    void registeredClass_returnsAssignedRiskLevel() {
        assertThat(registry.riskLevelOf(com.krdevops.springai.tools.DateTimeTool.class))
                .isEqualTo(McpToolRiskLevel.READ);
        assertThat(registry.riskLevelOf(com.krdevops.springai.tools.ThymeleafProjectWorkflowTool.class))
                .isEqualTo(McpToolRiskLevel.APPLY);
    }

    @Test
    void unregisteredClass_throwsImmediately() {
        assertThatThrownBy(() -> registry.riskLevelOf(String.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("위험 등급 레지스트리");
    }

    @Test
    void hasThirtyFourRegisteredToolClasses() {
        // WP2(Prototype Bridge 제거) 이후 실측치. 새 Tool을 등록할 때마다 이 값도 함께 갱신한다.
        assertThat(registry.registeredClasses()).hasSize(34);
    }
}
