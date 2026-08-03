package com.krdevops.springai.config.mcp;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * ARCH-0102/0103: {@code @Tool} 메서드에 붙은 {@link McpToolRisk}를 읽는다.
 * 어노테이션이 없는 {@code @Tool} 메서드가 하나라도 등록되면 즉시 실패한다(fail-closed).
 */
@Component
public class McpToolRiskAnnotationResolver {

    public McpToolRiskLevel resolve(Method toolMethod) {
        McpToolRisk annotation = toolMethod.getAnnotation(McpToolRisk.class);
        if (annotation == null) {
            throw new IllegalStateException(
                    "MCP @Tool 메서드에 @McpToolRisk가 없습니다: "
                    + toolMethod.getDeclaringClass().getName() + "#" + toolMethod.getName()
                    + " — 이 메서드 바로 위에 @McpToolRisk(McpToolRiskLevel.X)를 먼저 붙이세요.");
        }
        return annotation.value();
    }
}
