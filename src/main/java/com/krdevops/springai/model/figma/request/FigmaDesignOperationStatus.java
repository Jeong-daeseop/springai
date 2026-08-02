package com.krdevops.springai.model.figma.request;

/**
 * figma-design-operation-v1 schema의 status enum과 동일하다.
 * 허용 전이는 {@code FigmaDesignOperationStateService}가 강제한다.
 */
public enum FigmaDesignOperationStatus {
    ANALYZED,
    PREVIEW_READY,
    APPLY_REQUIRED,
    APPLIED,
    FAILED,
    CONFLICT,
    REJECTED
}
