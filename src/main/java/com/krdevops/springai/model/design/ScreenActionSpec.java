package com.krdevops.springai.model.design;

import com.krdevops.springai.model.design.role.ComponentState;
import com.krdevops.springai.model.design.role.SemanticRole;

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
}
