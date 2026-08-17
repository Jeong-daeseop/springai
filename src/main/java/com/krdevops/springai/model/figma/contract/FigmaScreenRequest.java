package com.krdevops.springai.model.figma.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * I-1: Figma 멀티 스크린 요청에서 개별 화면 정의.
 * 명세서 §4.5 기반.
 */
public record FigmaScreenRequest(
        @JsonProperty("name")
        String name,

        @JsonProperty("description")
        String description,

        /** R6-036(2026-08-18): 화면별로 실제 Bundle을 만들려면 각자의 DB 테이블이 필요하다. */
        @JsonProperty("database")
        String database,

        @JsonProperty("tableName")
        String tableName
) {
    public FigmaScreenRequest {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name은 필수입니다");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description은 필수입니다");
        }
    }

    /** database/tableName 도입 전 호출자 호환. */
    public FigmaScreenRequest(String name, String description) {
        this(name, description, null, null);
    }

    /**
     * 스크린 ID 반환 (name을 ID로 사용)
     */
    public String pageId() {
        return name;
    }
}
