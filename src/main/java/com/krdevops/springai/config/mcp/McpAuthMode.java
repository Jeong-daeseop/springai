package com.krdevops.springai.config.mcp;

/**
 * ARCH-WP1 §7 호환 전략의 단계적 롤아웃 모드.
 */
public enum McpAuthMode {
    /** 1단계: 미인증 호출을 허용하되 감사 로그만 남긴다(기존 permitAll과 동일한 실제 동작). */
    AUDIT_ONLY,
    /** 3단계: deny-by-default. 인증되지 않은 호출은 Tool 메서드 실행 전에 차단한다. */
    REQUIRED
}
