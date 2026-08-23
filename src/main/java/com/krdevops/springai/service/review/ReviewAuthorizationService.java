package com.krdevops.springai.service.review;

import com.krdevops.springai.model.review.ScreenReviewSession;
import org.springframework.stereotype.Service;

@Service
public class ReviewAuthorizationService {
    public void requireRole(ScreenReviewSession session, ScreenReviewSession.ReviewerRole role) {
        if (session == null || role == null || !session.reviewerRoles().contains(role)) throw new IllegalStateException("Review 역할 권한이 없습니다: " + role);
    }
    public void requireApprover(ScreenReviewSession session) { requireRole(session, ScreenReviewSession.ReviewerRole.APPROVER); }
}
