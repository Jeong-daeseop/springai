package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.ScreenFieldBinding;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** ScreenFieldBinding.control 문자열을 1차 KRDS Component 논리 타입으로 매핑한다(11번 §4 논리 타입 초기안). */
final class FieldComponentMapper {

    private static final Set<String> SUPPORTED_CONTROLS = Set.of("SELECT", "CHECKBOX", "TEXT", "TEXTAREA", "DATE", "NUMBER");

    private FieldComponentMapper() {
    }

    static String logicalType(ScreenFieldBinding field) {
        return switch (normalizedControl(field)) {
            case "SELECT" -> "krds.select";
            case "CHECKBOX" -> "krds.checkbox";
            default -> "krds.textField";
        };
    }

    static boolean isSupportedControl(ScreenFieldBinding field) {
        return SUPPORTED_CONTROLS.contains(normalizedControl(field));
    }

    static Map<String, Object> properties(ScreenFieldBinding field) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("label", field.label());
        properties.put("required", field.required());
        properties.put("control", field.control());
        if (!isSupportedControl(field)) {
            properties.put("unsupportedControl", true);
        }
        return properties;
    }

    private static String normalizedControl(ScreenFieldBinding field) {
        return field.control() == null ? "" : field.control().toUpperCase(Locale.ROOT);
    }
}
