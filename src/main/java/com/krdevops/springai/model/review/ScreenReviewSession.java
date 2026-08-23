package com.krdevops.springai.model.review;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record ScreenReviewSession(String sessionId, VersionedArtifactReference evidenceBundleRef,
                                  Set<ReviewerRole> reviewerRoles, List<Comment> comments,
                                  ApprovalDecision approvalDecision, Visibility visibility, Instant expiresAt) {
    public ScreenReviewSession {
        if (sessionId == null || sessionId.isBlank() || evidenceBundleRef == null || expiresAt == null) throw new IllegalArgumentException("Review Session 필수값이 누락되었습니다.");
        reviewerRoles = Set.copyOf(reviewerRoles == null ? Set.of() : reviewerRoles);
        if (reviewerRoles.isEmpty()) throw new IllegalArgumentException("reviewerRoles는 하나 이상 필요합니다.");
        comments = List.copyOf(comments == null ? List.of() : comments);
        approvalDecision = approvalDecision == null ? ApprovalDecision.PENDING : approvalDecision;
        visibility = visibility == null ? Visibility.PRIVATE : visibility;
    }
    public enum ReviewerRole { DESIGNER, BUSINESS, DEVELOPER, QA, APPROVER }
    public enum ApprovalDecision { PENDING, APPROVED, REJECTED, INVALIDATED }
    public enum Visibility { PRIVATE }
    public record Comment(String commentId, String author, String text, String evidenceLocation) { }
}
