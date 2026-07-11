package com.krdevops.springai.model.crud;

import java.util.Locale;

/**
 * Thymeleaf 공통 layout 파일 생성 정책.
 *
 * <p>CRUD/Board/MasterDetail 생성 시 공통 layout 파일(default·gnb·lnb·breadcrumb·footer)을
 * 어떻게 처리할지 결정한다. 3개 도메인이 공용으로 사용한다.
 *
 * <ul>
 *   <li>{@link #REUSE} — 기본값. layout 파일은 생성/저장하지 않고 기존 layout을 재사용한다.
 *       layout 파일이 없으면 저장 전에 실패하고 {@code generateThymeleafLayout} 실행을 안내한다.</li>
 *   <li>{@link #CREATE} — layout 파일까지 함께 생성한다.</li>
 *   <li>{@link #NONE} — layout 참조 없이 순수 화면 파일만 생성한다. (2차 구현 예정)</li>
 * </ul>
 */
public enum CrudLayoutMode {
    REUSE("reuse"),
    CREATE("create"),
    NONE("none");

    private final String value;

    CrudLayoutMode(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    /** 미입력(null/blank)이면 기본값 {@link #REUSE} 를 반환한다. */
    public static CrudLayoutMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return REUSE;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "reuse" -> REUSE;
            case "create" -> CREATE;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 layoutMode: " + raw + " (지원값: reuse, create, none)");
        };
    }
}
