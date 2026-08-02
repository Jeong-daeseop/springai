package com.krdevops.springai.config.mcp;

/**
 * MCP Tool 위험 등급 (ARCH-0101).
 * 등급 자체는 인증 요구 여부를 바꾸지 않는다 — {@link ToolAuthorizationPolicy}가
 * REQUIRED 모드에서는 모든 등급에 동일하게 인증을 요구한다. 등급은 감사 로그와
 * 향후 등급별 세분화 정책(예: APPLY만 별도 승인)의 근거로 사용한다.
 */
public enum McpToolRiskLevel {
    /** DB/파일/외부 부작용 없는 조회, 정적 템플릿·가이드 반환. */
    READ,
    /** 외부 시스템(OpenAI, Ollama, Figma API, Web Capture Extractor 등) 호출. */
    EXTERNAL,
    /** 애플리케이션 관리 DB 테이블에 쓰기(INSERT/UPDATE). */
    DB_WRITE,
    /** 로컬 파일 시스템에 생성 결과·아티팩트·설정을 씀. */
    FILE_WRITE,
    /** 승인된 변경을 대상 프로젝트/Figma Canvas에 실제 적용(Apply/Rollback). */
    APPLY
}
