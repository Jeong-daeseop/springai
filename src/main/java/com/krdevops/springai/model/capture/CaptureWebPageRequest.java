package com.krdevops.springai.model.capture;

public record CaptureWebPageRequest(
        String url, CaptureProfile profile, ViewportSpec viewport,
        ReadinessSpec readiness, String featureType) {
    public CaptureWebPageRequest {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("url은 필수입니다.");
        profile = profile == null ? CaptureProfile.LOCAL_JSP : profile;
        viewport = viewport == null ? ViewportSpec.desktop() : viewport;
        readiness = readiness == null ? new ReadinessSpec(null, null, 30000) : readiness;
        featureType = featureType == null || featureType.isBlank() ? "crud" : featureType;
    }
}
