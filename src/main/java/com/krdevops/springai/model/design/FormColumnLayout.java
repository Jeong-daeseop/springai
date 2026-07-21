package com.krdevops.springai.model.design;

import java.util.Locale;

/** 디자인 참조에서 전달되는 등록/수정 폼의 입력칸 배치 단수. */
public enum FormColumnLayout {
    SINGLE_COLUMN,
    TWO_COLUMN;

    public static FormColumnLayout from(String raw) {
        if (raw == null || raw.isBlank()) return SINGLE_COLUMN;
        String normalized = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 form column layout: " + raw
                            + " (지원값: single-column, two-column)", e);
        }
    }
}
