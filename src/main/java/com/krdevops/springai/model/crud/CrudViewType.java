package com.krdevops.springai.model.crud;

import java.util.Locale;

public enum CrudViewType {
    JSP("jsp"),
    THYMELEAF("thymeleaf");

    private final String value;

    CrudViewType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static CrudViewType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return JSP;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "jsp" -> JSP;
            case "thymeleaf", "th", "html" -> THYMELEAF;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 viewType: " + raw + " (지원값: jsp, thymeleaf)");
        };
    }
}
