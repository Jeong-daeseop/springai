package com.krdevops.springai.model.generation;

/** Preview를 전체 Screen 또는 특정 Section·Fragment로 제한하는 불변 요청 범위. */
public record PreviewScope(
        ScopeType type,
        String targetId
) {
    public PreviewScope {
        if (type == null) throw new IllegalArgumentException("Preview Scope type은 필수입니다.");
        if (targetId == null || targetId.isBlank()) throw new IllegalArgumentException("Preview Scope targetId는 필수입니다.");
        targetId = targetId.trim();
    }

    public static PreviewScope screen(String screenId) {
        return new PreviewScope(ScopeType.SCREEN, screenId);
    }

    public static PreviewScope section(String sectionId) {
        return new PreviewScope(ScopeType.SECTION, sectionId);
    }

    public static PreviewScope fragment(String fragmentId) {
        return new PreviewScope(ScopeType.FRAGMENT, fragmentId);
    }

    public enum ScopeType { SCREEN, SECTION, FRAGMENT }
}
