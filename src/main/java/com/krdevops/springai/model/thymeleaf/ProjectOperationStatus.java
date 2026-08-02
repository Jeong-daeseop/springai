package com.krdevops.springai.model.thymeleaf;

/**
 * I-5B: Project Operation 상태.
 * Thymeleaf 프로젝트 변환의 생명주기 상태를 정의한다.
 */
public enum ProjectOperationStatus {
    ANALYZED("분석 완료"),
    CONTRACT_READY("계약 준비"),
    PREVIEW_READY("미리보기 준비"),
    APPROVED("승인됨"),
    APPLIED("적용됨"),
    VALIDATED("검증됨"),
    
    FAILED("실패"),
    CONFLICT("충돌"),
    REJECTED("거절됨");

    public final String description;

    ProjectOperationStatus(String description) {
        this.description = description;
    }

    public boolean isTerminal() {
        return this == VALIDATED || this == FAILED || this == REJECTED;
    }

    public boolean isApplied() {
        return this == APPLIED || this == VALIDATED;
    }
}
