package com.krdevops.springai.model.capture;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record CaptureWebPageRequest(
        String url, CaptureProfile profile, ViewportSpec viewport,
        ReadinessSpec readiness, String featureType, @Nullable String storageStateRef,
        @Nullable List<InteractionStep> interactions) {
    public CaptureWebPageRequest(
            String url, CaptureProfile profile, ViewportSpec viewport,
            ReadinessSpec readiness, String featureType) {
        this(url, profile, viewport, readiness, featureType, null);
    }

    /** R7(04번 문서 §10) interactions 필드 도입 전 호출자 호환용. */
    public CaptureWebPageRequest(
            String url, CaptureProfile profile, ViewportSpec viewport,
            ReadinessSpec readiness, String featureType, @Nullable String storageStateRef) {
        this(url, profile, viewport, readiness, featureType, storageStateRef, null);
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
        if (interactions != null && interactions.size() > 20) {
            throw new IllegalArgumentException("interactions는 최대 20개까지 허용됩니다.");
        }
        interactions = interactions == null ? null : List.copyOf(interactions);
    }
}
