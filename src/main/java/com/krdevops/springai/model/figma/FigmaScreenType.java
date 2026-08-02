package com.krdevops.springai.model.figma;

/**
 * R1: Figma 화면 유형 — ScreenSpecification.archetype을 매핑.
 * 계획서 §5.3 R0-007 기반.
 */
public enum FigmaScreenType {
    /** LIST 화면 — 데이터 목록 조회 */
    LIST,

    /** FORM 화면 — 데이터 입력 */
    FORM,

    /** DETAIL 화면 — 데이터 상세 조회 */
    DETAIL,

    /** DASHBOARD 화면 — 대시보드/분석 */
    DASHBOARD
}
