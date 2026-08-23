package com.krdevops.springai.model.renderer;

/** 정상 생성 계약을 만족하지 못할 때 사용할 수 있는 대체 전략의 식별자. */
public enum RendererFallback {
    UNMAPPED_COMPONENT,
    UNSUPPORTED_VARIANT,
    RASTERIZED_BUSINESS_CONTROL,
    LEGACY_LAYOUT_REUSE
}
