package com.krdevops.springai.model.figma;

/**
 * R1: Figma 레이아웃 패턴 — ScreenSpecification.archetype 키워드로 판정.
 * 계획서 §5.3 R0-007 기반.
 */
public enum LayoutPattern {
    /** 표준 레이아웃 (헤더 + 콘텐츠 + 푸터) */
    STANDARD,

    /** Master-Detail 레이아웃 (마스터 + 상세 패널) */
    MASTER_DETAIL,

    /** 대시보드 레이아웃 (카드/차트 그리드) */
    DASHBOARD
}
