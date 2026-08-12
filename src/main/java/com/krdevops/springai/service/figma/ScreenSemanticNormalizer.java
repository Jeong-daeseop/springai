package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenActionSpec;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.FieldMode;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

/** 기존 화면 문자열을 버전 관리 가능한 Semantic Context로 정규화한다. */
@Component
public class ScreenSemanticNormalizer {

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("SEARCH", "검색"), Map.entry("CREATE", "등록"), Map.entry("SAVE", "저장"),
            Map.entry("UPDATE", "수정"), Map.entry("DELETE", "삭제"), Map.entry("CANCEL", "취소"),
            Map.entry("LIST", "목록"), Map.entry("VIEW_DETAIL", "상세"));

    public ScreenPattern pattern(PageSpec page) {
        String template = normalize(page.template());
        if (template.endsWith("_LIST")) return ScreenPattern.CRUD_LIST;
        if (template.endsWith("_DETAIL")) return ScreenPattern.CRUD_DETAIL;
        if (template.endsWith("_EDIT") || template.endsWith("_UPDATE")) return ScreenPattern.CRUD_EDIT;
        if (template.endsWith("_FORM") || template.endsWith("_REGIST") || template.endsWith("_CREATE")) {
            return page.actions().stream().map(this::normalize).anyMatch("UPDATE"::equals)
                    ? ScreenPattern.CRUD_EDIT : ScreenPattern.CRUD_CREATE;
        }
        throw new IllegalArgumentException("SCREEN_PATTERN_NOT_RESOLVED: " + page.template());
    }

    public SemanticRole fieldRole(ScreenFieldBinding field) {
        return switch (normalize(field.control())) {
            case "TEXT", "NUMBER", "DATE" -> SemanticRole.FIELD_TEXT;
            case "TEXTAREA" -> SemanticRole.FIELD_TEXTAREA;
            case "SELECT" -> SemanticRole.FIELD_SELECT;
            case "CHECKBOX" -> SemanticRole.FIELD_CHECKBOX;
            default -> throw new IllegalArgumentException(
                    "SEMANTIC_ROLE_NOT_DERIVED: 지원하지 않는 Control입니다: " + field.control());
        };
    }

    public FieldMode fieldMode(ScreenPattern pattern) {
        return pattern == ScreenPattern.CRUD_DETAIL ? FieldMode.READ_ONLY : FieldMode.EDITABLE;
    }

    public ScreenActionSpec action(String action) {
        String command = normalize(action);
        SemanticRole role = switch (command) {
            case "DELETE" -> SemanticRole.ACTION_DESTRUCTIVE;
            case "LIST", "CANCEL", "VIEW_DETAIL" -> SemanticRole.ACTION_SECONDARY;
            case "SEARCH", "CREATE", "SAVE", "UPDATE" -> SemanticRole.ACTION_PRIMARY;
            default -> throw new IllegalArgumentException("SEMANTIC_ROLE_NOT_DERIVED: 알 수 없는 Action입니다: " + action);
        };
        return new ScreenActionSpec(command.toLowerCase(Locale.ROOT), command, role,
                ACTION_LABELS.getOrDefault(command, command), ComponentState.DEFAULT);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
