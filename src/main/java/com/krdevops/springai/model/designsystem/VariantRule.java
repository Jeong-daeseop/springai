package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.design.role.SemanticRole;

import java.util.Map;

/** 화면 Context를 Component Contract의 논리 Variant 값으로 변환하는 결정 규칙. */
public record VariantRule(
        String ruleId,
        int priority,
        SemanticRole role,
        Map<String, String> when,
        Map<String, String> result
) {
    public VariantRule {
        if (ruleId == null || ruleId.isBlank()) {
            throw new IllegalArgumentException("ruleId는 필수입니다.");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority는 0 이상이어야 합니다.");
        }
        if (role == null) {
            throw new IllegalArgumentException("rule role은 필수입니다.");
        }
        when = when == null ? Map.of() : Map.copyOf(when);
        result = result == null ? Map.of() : Map.copyOf(result);
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Variant Rule result는 비어 있을 수 없습니다.");
        }
    }
}
