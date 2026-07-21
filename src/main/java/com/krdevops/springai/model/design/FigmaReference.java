package com.krdevops.springai.model.design;

public record FigmaReference(String fileKey, String nodeId) {
    public FigmaReference {
        if (fileKey == null || fileKey.isBlank()) {
            throw new IllegalArgumentException("Figma fileKey가 필요합니다.");
        }
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("Figma nodeId가 필요합니다.");
        }
    }
}
