package com.krdevops.springai.model.design;

import java.util.Locale;

/** 디자인 참조에서 전달되는 목록 화면 검색 패널의 유무/위치. */
public enum SearchPanelPlacement {
    ABOVE_TABLE,
    NONE;

    public static SearchPanelPlacement from(String raw) {
        if (raw == null || raw.isBlank()) return ABOVE_TABLE;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 search panel placement: " + raw
                            + " (지원값: above-table, none)", e);
        }
    }
}
