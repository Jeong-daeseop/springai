package com.krdevops.springai.controller;

/**
 * MR-A09: Refinement 상태·낙관적 잠금 충돌 표준 오류(409). 사용 코드:
 * {@code REFINEMENT_BASE_STALE}, {@code REFINEMENT_CONFLICT}, {@code REFINEMENT_NOT_APPROVED}.
 */
public class FigmaRefinementConflictException extends RuntimeException {

    private final String code;

    public FigmaRefinementConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
