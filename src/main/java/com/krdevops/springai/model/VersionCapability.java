package com.krdevops.springai.model;

/** 버전별 런타임 특성 — 불변 스냅샷 */
public record VersionCapability(
        // ── boolean Capability ──
        boolean jakarta,           // javax → jakarta
        boolean spring6,           // Spring Framework 6.x
        boolean boot3,             // Spring Boot 3.x
        boolean java17,            // Java 17 toolchain
        boolean egovParent,        // 전용 Parent POM
        boolean hyphenArtifactId,  // 5.0+ artifactId 명명 규칙
        boolean myBatisSpring3,    // mybatis-spring 3.x
        // ── 버전 문자열 (독립 해석) ──
        String  egovVersion,       // "5.0" / "4.3" (축약형 통일)
        String  javaVersion,       // "17" / "11"
        String  springVersion,     // "6.2.11" / "5.3.37"
        String  springBootVersion, // "3.5.6" / "2.7.18"
        String  securityVersion    // "6.5.5" / "5.8.13"
) {
    /** 축약 레이블 — 사용자 표시/ProjectContext용 */
    public String label() { return egovVersion; }
}
