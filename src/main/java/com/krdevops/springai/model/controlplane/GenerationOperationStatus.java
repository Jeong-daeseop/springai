package com.krdevops.springai.model.controlplane;

/** 서로 다른 생성 경로의 상태를 조회용으로 정규화한 값. */
public enum GenerationOperationStatus {
    PREVIEW_READY,
    APPROVAL_REQUIRED,
    APPROVED,
    APPLYING,
    APPLIED,
    CONFLICT,
    FAILED,
    VALIDATED,
    REJECTED,
    UNKNOWN
}
