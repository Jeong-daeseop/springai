package com.krdevops.springai.model.thymeleaf;

/**
 * R6-053: JSP·Controller 정적 분석 증거로부터 추정한 화면 유형(LIST/FORM/DETAIL) 제안.
 * {@link ThymeleafBindingPreviewRequest#screenRole()}을 대체하지 않는 참고용 힌트다 — 근거가
 * 부족하면 {@code suggestedRole}이 null이고, 이는 오류가 아니라 "판정 불가"를 뜻한다.
 */
public record ScreenRoleSuggestion(
        LegacyScreenRole suggestedRole,
        double confidence,
        String reasoning
) {
    public ScreenRoleSuggestion {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence는 0.0~1.0 범위여야 합니다.");
        }
        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalArgumentException("reasoning은 필수입니다.");
        }
    }

    public boolean resolved() {
        return suggestedRole != null;
    }
}
