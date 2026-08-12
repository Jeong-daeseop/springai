package com.krdevops.springai.service.figma.builder;

import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.SemanticRole;

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
            case "TEXTAREA" -> "krds.textarea";
            case "TEXT", "NUMBER", "DATE" -> "krds.textField";
            default -> throw new IllegalArgumentException(
                    "SEMANTIC_ROLE_NOT_DERIVED: 지원하지 않는 Control입니다: " + field.control());
        };
    }

    static boolean isSupportedControl(ScreenFieldBinding field) {
        return SUPPORTED_CONTROLS.contains(normalizedControl(field));
    }

    static Map<String, Object> properties(ScreenFieldBinding field) {
        return properties(field, FieldMode.EDITABLE);
    }

    static Map<String, Object> properties(ScreenFieldBinding field, FieldMode mode) {
        Map<String, Object> properties = new LinkedHashMap<>();
        SemanticRole role = switch (normalizedControl(field)) {
            case "SELECT" -> SemanticRole.FIELD_SELECT;
            case "CHECKBOX" -> SemanticRole.FIELD_CHECKBOX;
            case "TEXTAREA" -> SemanticRole.FIELD_TEXTAREA;
            case "TEXT", "NUMBER", "DATE" -> SemanticRole.FIELD_TEXT;
            default -> throw new IllegalArgumentException(
                    "SEMANTIC_ROLE_NOT_DERIVED: 지원하지 않는 Control입니다: " + field.control());
        };
        properties.put("semanticRole", role.code());
        properties.put("label", field.label());
        properties.put("required", field.required());
        properties.put("control", field.control());
        properties.put("mode", mode.name());
        properties.put("state", mode == FieldMode.READ_ONLY ? "READ_ONLY" : "DEFAULT");
        return properties;
    }

    private static String normalizedControl(ScreenFieldBinding field) {
        return field.control() == null ? "" : field.control().toUpperCase(Locale.ROOT);
    }
}
