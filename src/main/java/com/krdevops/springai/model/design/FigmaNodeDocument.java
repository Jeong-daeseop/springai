package com.krdevops.springai.model.design;

import com.fasterxml.jackson.databind.JsonNode;

public record FigmaNodeDocument(String fileVersion, JsonNode document) {
    public FigmaNodeDocument {
        if (fileVersion == null || fileVersion.isBlank()) {
            throw new IllegalArgumentException("Figma 파일 버전이 필요합니다.");
        }
        if (document == null || !document.isObject()) {
            throw new IllegalArgumentException("Figma 노드 문서가 필요합니다.");
        }
    }
}
