package com.krdevops.springai.model.design;

public record FigmaDesignSourceMetadata(String fileKey, String nodeId, String fileVersion)
        implements DesignSourceMetadata {
    @Override
    public DesignSourceType sourceType() {
        return DesignSourceType.FIGMA;
    }
}
