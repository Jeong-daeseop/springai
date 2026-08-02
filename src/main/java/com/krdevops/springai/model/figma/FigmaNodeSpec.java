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

        @JsonProperty("logicalType")
        String logicalType,

        @JsonProperty("properties")
        Map<String, Object> properties,

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
    }

    public String type() {
        return logicalType;
    }
}
