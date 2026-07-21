package com.krdevops.springai.model.design;

public record WebCaptureDesignSourceMetadata(
        String artifactId,
        String documentKey,
        String contentHash,
        String renderedDocumentSchemaVersion
) implements DesignSourceMetadata {
    @Override
    public DesignSourceType sourceType() {
        return DesignSourceType.WEB_CAPTURE;
    }
}
