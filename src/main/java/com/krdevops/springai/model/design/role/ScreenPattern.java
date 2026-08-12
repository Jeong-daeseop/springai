package com.krdevops.springai.model.design.role;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

public enum ScreenPattern {
    CRUD_LIST("crud.list"),
    CRUD_DETAIL("crud.detail"),
    CRUD_CREATE("crud.create"),
    CRUD_EDIT("crud.edit");

    private final String code;

    ScreenPattern(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ScreenPattern fromCode(String code) {
        return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("알 수 없는 Screen Pattern입니다: " + code));
    }
}
