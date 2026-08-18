package com.krdevops.springai.model.capture;

import org.jspecify.annotations.Nullable;

public record CaptureWebPageRequest(
        String url, CaptureProfile profile, ViewportSpec viewport,
        ReadinessSpec readiness, String featureType, @Nullable String storageStateRef) {
    public CaptureWebPageRequest(
            String url, CaptureProfile profile, ViewportSpec viewport,
            ReadinessSpec readiness, String featureType) {
        this(url, profile, viewport, readiness, featureType, null);
    }

    public CaptureWebPageRequest {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url은 필수입니다.");
        profile = profile == null ? CaptureProfile.LOCAL_WEB : profile;
        viewport = viewport == null ? ViewportSpec.desktop() : viewport;
        readiness = readiness == null ? new ReadinessSpec(null, null, 30000) : readiness;
        featureType = featureType == null || featureType.isBlank() ? "crud" : featureType;
        if (storageStateRef != null && !storageStateRef.isBlank()
                && !storageStateRef.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")) {
            throw new IllegalArgumentException("storageStateRef는 Extractor가 발급한 UUID여야 합니다.");
        }
        storageStateRef = storageStateRef == null || storageStateRef.isBlank() ? null : storageStateRef;
    }
}
