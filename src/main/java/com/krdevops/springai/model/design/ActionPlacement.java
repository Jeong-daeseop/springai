package com.krdevops.springai.model.design;

import java.util.Locale;

/** 디자인 참조에서 전달되는 목록 화면 등록 버튼(주요 액션)의 배치 위치. */
public enum ActionPlacement {
    TOP_RIGHT,
    BOTTOM_RIGHT;

    public static ActionPlacement from(String raw) {
        if (raw == null || raw.isBlank()) return TOP_RIGHT;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 action placement: " + raw
                            + " (지원값: top-right, bottom-right)", e);
        }
    }
}
