package com.krdevops.springai.config.mcp;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ARCH-0101/0102: MCP {@code @Tool} 메서드에 위험 등급을 선언한다.
 *
 * <p>{@code @Tool} 바로 위에 붙인다. {@code McpConfig}가 등록된 모든 {@code ToolCallback}에
 * 대해 이 어노테이션 존재를 기동 시 강제한다(ARCH-0103) — 붙이지 않은 {@code @Tool} 메서드가
 * 하나라도 있으면 애플리케이션 컨텍스트가 뜨지 않는다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface McpToolRisk {
    McpToolRiskLevel value();
}
