package com.krdevops.springai.model.design.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Figma 컴포넌트명이 아닌 화면에서의 의미와 책임을 나타낸다. */
public enum SemanticRole {
    PAGE_HEADER("page.header"),
    SEARCH_PANEL("search.panel"),
    DATA_TABLE("data.table"),
    DATA_TABLE_CELL("data.table.cell"),
    DATA_PAGINATION("data.pagination"),
    FORM_CONTAINER("form.container"),
    FORM_SECTION("form.section"),
    FIELD_TEXT("field.text"),
    FIELD_TEXTAREA("field.textarea"),
    FIELD_SELECT("field.select"),
    FIELD_CHECKBOX("field.checkbox"),
    ACTION_PRIMARY("action.primary"),
    ACTION_SECONDARY("action.secondary"),
    ACTION_DESTRUCTIVE("action.destructive");

    private final String code;

    SemanticRole(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static SemanticRole fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 Semantic Role입니다: " + code));
    }
}
