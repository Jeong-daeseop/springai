package com.krdevops.springai.model.capture;

import org.jspecify.annotations.Nullable;

/**
 * 04번 문서 R6(§9): 인증 세션(로그인) 발급 요청. 원문 username/password를 담고 있어
 * {@code WebCaptureSessionController}(운영자 전용 REST, X-API-Key 인증)에서만 받는다 —
 * MCP(LLM) 경로에는 절대 노출하지 않는다. 발급된 {@code storageStateRef}(opaque UUID)만
 * {@code captureWebPage} MCP Tool로 전달한다.
 */
public record WebCaptureSessionRequest(
        String loginUrl,
        @Nullable String preClickSelector,
        String usernameSelector,
        String username,
        String passwordSelector,
        String password,
        String submitSelector,
        @Nullable String successSelector,
        @Nullable Integer timeoutMillis) {
    public WebCaptureSessionRequest {
        if (loginUrl == null || loginUrl.isBlank()) {
            throw new IllegalArgumentException("loginUrl은 필수입니다.");
        }
        if (usernameSelector == null || usernameSelector.isBlank()) {
            throw new IllegalArgumentException("usernameSelector는 필수입니다.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username은 필수입니다.");
        }
        if (passwordSelector == null || passwordSelector.isBlank()) {
            throw new IllegalArgumentException("passwordSelector는 필수입니다.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("password는 필수입니다.");
        }
        if (submitSelector == null || submitSelector.isBlank()) {
            throw new IllegalArgumentException("submitSelector는 필수입니다.");
        }
    }
}
