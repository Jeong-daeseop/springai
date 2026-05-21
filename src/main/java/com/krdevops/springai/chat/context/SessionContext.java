package com.krdevops.springai.chat.context;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SessionContext {

    private static final String DEFAULT_SESSION_ID = "default";
    private static final ThreadLocal<String> CURRENT_SESSION_ID = new ThreadLocal<>();

    public static void setCurrentSessionId(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            log.warn("빈 세션 ID 설정 시도, 기본값 사용");
            CURRENT_SESSION_ID.set(DEFAULT_SESSION_ID);
        } else {
            log.debug("세션 ID 설정: {}", sessionId);
            CURRENT_SESSION_ID.set(sessionId);
        }
    }

    public static String getCurrentSessionId() {
        String sessionId = CURRENT_SESSION_ID.get();
        if (sessionId == null) {
            log.warn("세션 ID가 설정되지 않음, 기본값 사용");
            return DEFAULT_SESSION_ID;
        }
        return sessionId;
    }

    public static void clear() {
        String currentSession = CURRENT_SESSION_ID.get();
        log.debug("세션 컨텍스트 정리: {}", currentSession);
        CURRENT_SESSION_ID.remove();
    }

    public static boolean isDefaultSession() {
        return DEFAULT_SESSION_ID.equals(getCurrentSessionId());
    }
}
