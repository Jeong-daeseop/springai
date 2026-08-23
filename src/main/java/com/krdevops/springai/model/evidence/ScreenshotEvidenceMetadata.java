package com.krdevops.springai.model.evidence;

import com.krdevops.springai.model.artifact.ContentHashes;

public record ScreenshotEvidenceMetadata(
        Viewport viewport, int width, int height, String artifactHash, String capturedAt
) {
    public ScreenshotEvidenceMetadata {
        if (viewport == null || width <= 0 || height <= 0) throw new IllegalArgumentException("Screenshot viewport·크기는 필수입니다.");
        ContentHashes.requireValid(artifactHash);
        if (capturedAt == null || capturedAt.isBlank()) throw new IllegalArgumentException("capturedAt은 필수입니다.");
    }
    public enum Viewport { DESKTOP, TABLET, MOBILE }
}
