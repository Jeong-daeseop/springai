package com.krdevops.springai.model.thymeleaf;

/**
 * I-5B: JSP→Thymeleaf 전환 1개 화면의 Project Operation 상태.
 * {@code FigmaDesignOperationStatus}와 이름은 비슷하나 Canvas 적용과 파일 적용의 트랜잭션 경계가
 * 달라 통합하지 않는다(§3.2). 허용 전이는 {@code ThymeleafConversionOperationStateService}가 강제한다.
 */
public enum ThymeleafConversionOperationStatus {
    ANALYZED,
    CONTRACT_READY,
    PREVIEW_READY,
    APPROVED,
    APPLIED,
    VALIDATED,
    FAILED,
    CONFLICT,
    REJECTED
}
