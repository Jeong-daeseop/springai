package com.krdevops.springai.model.thymeleaf;

/** 분석 대상 화면의 역할. Figma의 {@code screenType}과 의미는 비슷하나 별도 열거형으로 둔다(§3.2). */
public enum LegacyScreenRole {
    LIST,
    FORM,
    DETAIL
}
