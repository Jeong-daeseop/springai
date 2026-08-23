package com.krdevops.springai.service.review;

import com.krdevops.springai.model.review.ScreenReviewSession;
import org.springframework.stereotype.Repository;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class ScreenReviewSessionRepository {
    private final Map<String, ScreenReviewSession> sessions = new ConcurrentHashMap<>();
    public ScreenReviewSession save(ScreenReviewSession session) {
        if (session == null) throw new IllegalArgumentException("session은 필수입니다.");
        if (sessions.putIfAbsent(session.sessionId(), session) != null) throw new IllegalStateException("Session ID가 이미 존재합니다.");
        return session;
    }
    public Optional<ScreenReviewSession> find(String sessionId) { return Optional.ofNullable(sessions.get(sessionId)); }
}
