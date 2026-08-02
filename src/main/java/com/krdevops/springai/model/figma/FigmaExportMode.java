package com.krdevops.springai.model.figma;

/**
 * R1: Figma 화면 생성/갱신 모드 — 계획서 §5.3 R0-005 기반.
 */
public enum FigmaExportMode {
    /** 미리보기 (적용 전 검증용) */
    PREVIEW,

    /** 신규 화면 생성 */
    CREATE,

    /** 기존 화면과 병합 (Instance 재사용, 신규 노드만 생성) */
    MERGE,

    /** 기존 화면 교체 (Archive 후 재생성) */
    REPLACE,

    /** 생성 생략 */
    SKIP,

    /** 최종 완성 (검증 후 적용) */
    FINAL
}
