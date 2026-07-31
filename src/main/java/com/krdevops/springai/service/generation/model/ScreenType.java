package com.krdevops.springai.service.generation.model;

/** 단일 화면 종류. label()은 기존 layerKey 접미사(jspList/thymeleafList 등)와 응답 문자열에 사용된다. */
public enum ScreenType {
    LIST("List"),
    DETAIL("Detail"),
    REGIST("Regist"),
    UPDT("Updt");

    private final String label;

    ScreenType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
