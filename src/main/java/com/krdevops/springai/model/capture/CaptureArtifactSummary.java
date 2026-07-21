package com.krdevops.springai.model.capture;

import java.time.LocalDateTime;
import java.util.List;

public record CaptureArtifactSummary(
        String artifactId, CaptureStatus status, String documentKey, String contentHash,
        int nodeCount, int assetCount, List<CaptureWarning> warnings, LocalDateTime createdAt) {
    public CaptureArtifactSummary {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
