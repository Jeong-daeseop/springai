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
        return properties(field, field.mode() == null ? FieldMode.EDITABLE : field.mode());
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
        properties.put("dataRole", field.dataRole().name());
        properties.put("label", field.label());
        // Figma Component Set에는 별도의 value Property가 없으므로
        // visual candidate에서 확인 가능한 업무 샘플 값을 placeholder로 전달한다.
        // 실제 런타임 데이터 바인딩은 화면 구현 단계에서 대체되며,
        // 등록/수정/상세 Bundle 모두 빈 입력처럼 보이지 않도록 한다.
        properties.put("placeholder", sampleValue(field));
        properties.put("sampleValue", sampleValue(field));
        properties.put("required", field.required());
        properties.put("labelRequired", field.required());
        properties.put("helperText", field.required()
                ? field.label() + "은(는) 필수 입력 항목입니다."
                : field.label() + "을(를) 확인하세요.");
        properties.put("errorMessage", field.required()
                ? field.label() + "을(를) 입력하세요."
                : "");
        properties.put("control", field.control());
        properties.put("mode", mode.name());
        properties.put("state", mode == FieldMode.READ_ONLY ? "READ_ONLY" : "DEFAULT");
        return properties;
    }

    private static String sampleValue(ScreenFieldBinding field) {
        String id = field.id() == null ? "" : field.id().toLowerCase(Locale.ROOT);
        String role = field.dataRole() == null ? "" : field.dataRole().name().toLowerCase(Locale.ROOT);
        String key = id + " " + role;
        if (key.contains("writer") || key.contains("author")) return "홍길동";
        if (key.contains("contact") || key.contains("phone")) return "012-0000-1234";
        if (key.contains("email")) return "hong@example.com";
        if (key.contains("title")) return "시스템 이용 방법 문의";
        if (key.contains("content")) return "Q&A 서비스 이용 방법을 문의합니다.";
        if (key.contains("createdat") || key.contains("created_at")) return "2026-08-01";
        if (key.contains("viewcount") || key.contains("view_count")) return "12";
        if (key.contains("status")) return "답변대기";
        if (key.contains("private") || key.contains("emailreply")) return "선택하지 않음";
        return field.label() == null ? "입력 예시" : field.label() + " 입력 예시";
    }

    private static String normalizedControl(ScreenFieldBinding field) {
        return field.control() == null ? "" : field.control().toUpperCase(Locale.ROOT);
    }
}
