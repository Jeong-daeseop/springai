package com.krdevops.springai.model.figma.contract;

/**
 * I-0: Figma 디자인 요청의 7가지 기본 유형.
 * 명세서 §4.1~4.7 기반.
 */
public enum FigmaDesignRequestType {
    /** 텍스트 설명으로 새 화면 생성 */
    TEXT_DESCRIPTION,

    /** 기존 Figma 화면을 참조하여 새 화면 생성 */
    REFERENCE_STYLE,

    /** 기존 화면 수정 */
    MODIFY_EXISTING,

    /** 이미지/스크린샷을 참조하여 화면 생성 */
    IMAGE_REFERENCE,

    /** 여러 화면을 한번에 생성 (플로우 단위) */
    MULTI_SCREEN_FLOW,

    /** 특정 컴포넌트를 지정하여 화면 생성 */
    COMPONENT_SPECIFIED,

    /** 플랫폼/반응형 변환 (Desktop ↔ Mobile ↔ Tablet) */
    PLATFORM_CONVERT
}
