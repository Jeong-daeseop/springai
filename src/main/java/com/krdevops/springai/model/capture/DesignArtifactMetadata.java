package com.krdevops.springai.model.capture;

import java.time.LocalDateTime;

public record DesignArtifactMetadata(
        String artifactId, String documentKey, String contentHash,
        String schemaVersion, LocalDateTime createdAt) {
}
