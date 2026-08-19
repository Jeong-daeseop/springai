package com.krdevops.springai.model.capture;

/** 04번 문서 R6(§9): extractor {@code POST /v1/sessions} 응답 — opaque sessionId와 만료 시각만 담는다. */
public record WebCaptureSessionResponse(String sessionId, String expiresAt) {
}
