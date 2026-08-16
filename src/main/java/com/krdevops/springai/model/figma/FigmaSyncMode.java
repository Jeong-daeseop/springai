package com.krdevops.springai.model.figma;

/**
 * R1: Figma 동기화 모드 — Published Component Instance 재사용 정책.
 */
public enum FigmaSyncMode {
    /** 미리보기 (적용 전 검증용) */
    PREVIEW,

    /** 기존 Instance 전체 교체 */
    REPLACE,

    /** 기존 Instance와 병합 */
    MERGE
}
