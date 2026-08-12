package com.krdevops.springai.model.figma;

import com.krdevops.springai.model.design.role.SemanticRole;

import java.util.Map;

/** 서버에서 완전히 해결되어 Plugin이 추가 추론 없이 적용할 Published Instance 참조. */
public record ResolvedComponentRef(
        SemanticRole role,
        String logicalType,
        String componentSetKey,
        String variantKey,
        Map<String, String> variantProperties,
        Map<String, Object> componentProperties,
        String contractVersion,
        String ruleSetVersion,
        String ruleId,
        String contextHash
) {
    public ResolvedComponentRef {
        if (role == null || logicalType == null || logicalType.isBlank()
                || componentSetKey == null || componentSetKey.isBlank()) {
            throw new IllegalArgumentException("해결된 Component의 role, logicalType, componentSetKey는 필수입니다.");
        }
        variantProperties = variantProperties == null ? Map.of() : Map.copyOf(variantProperties);
        componentProperties = componentProperties == null ? Map.of() : Map.copyOf(componentProperties);
    }
}
