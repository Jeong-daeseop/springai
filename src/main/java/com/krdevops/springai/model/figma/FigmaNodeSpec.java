package com.krdevops.springai.model.figma;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * R1: Figma 논리 노드 (화면을 구성하는 컴포넌트/레이아웃 요소).
 * logicalNodeId로 추적, Published Instance와 매핑.
 * 계획서 12번 R5-020 기반.
 */
public record FigmaNodeSpec(
        @JsonProperty("logicalNodeId")
        String logicalNodeId,

        @JsonProperty("nodeType")
        NodeType nodeType,

        @JsonProperty("type")
        String type,

        @JsonProperty("properties")
        Map<String, Object> properties,

        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        @JsonProperty("componentResolution")
        ResolvedComponentRef componentResolution,

        @JsonProperty("children")
        List<FigmaNodeSpec> children
) {
    public enum NodeType {
        PAGE,
        SECTION,
        COMPONENT,
        TEXT,
        SLOT,
        REPEAT
    }

    public FigmaNodeSpec {
        if (logicalNodeId == null || logicalNodeId.isBlank()) {
            throw new IllegalArgumentException("logicalNodeId는 필수입니다");
        }
        if (nodeType == null) {
            throw new IllegalArgumentException("nodeType은 필수입니다");
        }
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** componentResolution 도입 전 호출자와 v1 Builder 호환. */
    public FigmaNodeSpec(
            String logicalNodeId,
            NodeType nodeType,
            String type,
            Map<String, Object> properties,
            List<FigmaNodeSpec> children
    ) {
        this(logicalNodeId, nodeType, type, properties, null, children);
    }
}
