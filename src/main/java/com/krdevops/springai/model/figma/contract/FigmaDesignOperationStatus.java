package com.krdevops.springai.model.figma.contract;

/**
 * I-1: Figma 디자인 요청 처리 상태 머신.
 * 명세서 §3.2 데이터 흐름 기반.
 */
public enum FigmaDesignOperationStatus {
    /** 초기 상태: 요청 분석 완료 */
    ANALYZED,

    /** 미리보기 생성 완료 */
    PREVIEW_READY,

    /** 사용자 승인 대기 중 */
    APPLY_REQUIRED,

    /** Plugin 적용 완료 (최종 상태) */
    APPLIED,

    /** 분석/생성 실패 (최종 상태) */
    FAILED,

    /** Source revision 불일치로 충돌 (최종 상태) */
    CONFLICT,

    /** 사용자 거부 (최종 상태) */
    REJECTED
}
