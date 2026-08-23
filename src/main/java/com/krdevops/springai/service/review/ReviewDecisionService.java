package com.krdevops.springai.service.review;

import com.krdevops.springai.model.review.ScreenReviewSession;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

@Service
public class ReviewDecisionService {
    public Decision decide(ScreenReviewSession session, ScreenReviewSession.ApprovalDecision decision, String actor) {
        if (session == null || decision == null || actor == null || actor.isBlank()) throw new IllegalArgumentException("Decision 필수값이 누락되었습니다.");
        if (decision == ScreenReviewSession.ApprovalDecision.APPROVED) new ReviewAuthorizationService().requireApprover(session);
        return new Decision(decision, actor.trim(), Instant.now());
    }
    public record Decision(ScreenReviewSession.ApprovalDecision decision, String actor, Instant decidedAt) { }
}
