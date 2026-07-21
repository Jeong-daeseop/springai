package com.krdevops.springai.model.design;

import java.util.Locale;

/** 디자인 참조에서 전달되는 표/폼의 시각적 밀도. */
public enum LayoutDensity {
    STANDARD,
    COMPACT,
    COMFORTABLE;

    public static LayoutDensity from(String raw) {
        if (raw == null || raw.isBlank()) return STANDARD;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 layout density: " + raw
                            + " (지원값: STANDARD, COMPACT, COMFORTABLE)", e);
        }
    }
}
