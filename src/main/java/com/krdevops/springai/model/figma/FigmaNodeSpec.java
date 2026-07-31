package com.krdevops.springai.model.figma;

import java.util.List;
import java.util.Map;

/** 화면과 컴포넌트의 공통 계층 모델. Figma Plugin이 Instance/Frame을 조립하는 논리 트리다. */
public record FigmaNodeSpec(
        @jakarta.validation.constraints.NotBlank String logicalNodeId,
        @jakarta.validation.constraints.NotNull NodeType nodeType,
        @jakarta.validation.constraints.NotBlank String type,
        @jakarta.validation.constraints.NotNull Map<String, Object> properties,
        @jakarta.validation.Valid @jakarta.validation.constraints.NotNull List<FigmaNodeSpec> children
) {
    public FigmaNodeSpec {
        if (logicalNodeId == null || logicalNodeId.isBlank()) {
            throw new IllegalArgumentException("logicalNodeId는 필수입니다.");
        }
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** R0-004: 화면 트리에서 이 노드가 맡는 구조적 역할. */
    public enum NodeType {
        PAGE,
        SECTION,
        COMPONENT,
        TEXT,
        SLOT,
        REPEAT
    }
}
