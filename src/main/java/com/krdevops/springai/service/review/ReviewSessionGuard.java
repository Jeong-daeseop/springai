package com.krdevops.springai.service.review;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.review.ScreenReviewSession;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class ReviewSessionGuard {
    public void requireActive(ScreenReviewSession session) {
        if (session == null || !session.expiresAt().isAfter(Instant.now())) throw new IllegalStateException("Review Session이 만료되었습니다.");
    }
    public void requireEvidenceUnchanged(ScreenReviewSession session, VersionedArtifactReference current) {
        if (session == null || current == null || !session.evidenceBundleRef().identifies(current)) throw new IllegalStateException("Evidence Bundle 변경으로 Review 승인이 무효화되었습니다.");
    }
    public void requireApplyPermission(ScreenReviewSession session, boolean applyPermission) {
        requireActive(session);
        if (!applyPermission) throw new IllegalStateException("Review 승인과 Apply 권한이 분리되어 Apply 권한이 없습니다.");
        if (session.approvalDecision() != ScreenReviewSession.ApprovalDecision.APPROVED) throw new IllegalStateException("승인되지 않은 Review Session입니다.");
    }
}
