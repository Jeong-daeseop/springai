package com.krdevops.springai.model.design;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.SemanticRole;

import java.util.Locale;

/** 문자열 Action을 의미·표시·상태로 분리한 v2 화면 액션 계약. */
public record ScreenActionSpec(
        String id,
        String command,
        SemanticRole role,
        String label,
        ComponentState state
) {
    public ScreenActionSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("action id는 필수입니다.");
        }
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("action command는 필수입니다.");
        }
        if (role == null || !role.code().startsWith("action.")) {
            throw new IllegalArgumentException("action Semantic Role이 필요합니다.");
        }
        state = state == null ? ComponentState.DEFAULT : state;
    }

    /** 기존 ScreenSpecification JSON의 문자열 Action을 읽기 위한 단방향 호환 경계. */
    public static ScreenActionSpec fromLegacyCommand(String rawCommand) {
        return legacy(rawCommand);
    }

    /** v1 문자열과 v2 객체를 모두 읽되, 직렬화 결과는 항상 v2 객체로 고정한다. */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ScreenActionSpec fromJson(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new IllegalArgumentException("Action은 null일 수 없습니다.");
        }
        if (node.isTextual()) {
            return legacy(node.asText());
        }
        if (!node.isObject()) {
            throw new IllegalArgumentException("Action은 문자열 또는 객체여야 합니다.");
        }
        return new ScreenActionSpec(
                text(node, "id"), text(node, "command"),
                SemanticRole.fromCode(text(node, "role")), text(node, "label"),
                node.hasNonNull("state") ? ComponentState.valueOf(text(node, "state")) : ComponentState.DEFAULT);
    }

    private static String text(JsonNode node, String property) {
        JsonNode value = node.get(property);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static ScreenActionSpec legacy(String rawCommand) {
        String command = rawCommand == null ? "" : rawCommand.trim().toUpperCase(Locale.ROOT);
        SemanticRole role = switch (command) {
            case "DELETE" -> SemanticRole.ACTION_DESTRUCTIVE;
            case "LIST", "CANCEL", "VIEW_DETAIL", "BACK" -> SemanticRole.ACTION_SECONDARY;
            case "SEARCH", "CREATE", "SAVE", "UPDATE" -> SemanticRole.ACTION_PRIMARY;
            default -> throw new IllegalArgumentException("알 수 없는 레거시 Action입니다: " + rawCommand);
        };
        String label = switch (command) {
            case "SEARCH" -> "검색";
            case "CREATE" -> "등록";
            case "SAVE" -> "저장";
            case "UPDATE" -> "수정";
            case "DELETE" -> "삭제";
            case "CANCEL" -> "취소";
            case "LIST", "BACK" -> "목록";
            case "VIEW_DETAIL" -> "상세";
            default -> command;
        };
        return new ScreenActionSpec(command.toLowerCase(Locale.ROOT), command, role, label, ComponentState.DEFAULT);
    }
}
