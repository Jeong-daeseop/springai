package com.krdevops.springai.service.figma;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * I-6E: Figma 검수 workflow (comment, approval, revision flow).
 */
@Service
public class ApprovalWorkflow {

    /**
     * 검수 Board를 생성합니다.
     */
    public Map<String, Object> createApprovalBoard(
            String figmaFileId,
            String screenName,
            String templatePath) {

        Map<String, Object> board = new HashMap<>();
        board.put("figmaFileId", figmaFileId);
        board.put("boardName", "Review: " + screenName);
        board.put("screenName", screenName);
        board.put("templatePath", templatePath);
        board.put("status", "DRAFT");
        board.put("createdAt", System.currentTimeMillis());
        board.put("comments", new ArrayList<>());
        board.put("approvals", new ArrayList<>());

        return board;
    }

    /**
     * 검수 comment를 Board에 추가합니다.
     */
    public Map<String, Object> addReviewComment(
            String boardId,
            String reviewer,
            String comment,
            String severity) { // BLOCKER, MINOR, INFO

        Map<String, Object> commentObj = new HashMap<>();
        commentObj.put("id", "comment-" + System.nanoTime());
        commentObj.put("reviewer", reviewer);
        commentObj.put("text", comment);
        commentObj.put("severity", severity);
        commentObj.put("timestamp", Instant.now().toEpochMilli());
        commentObj.put("status", "PENDING");

        return commentObj;
    }

    /**
     * 검수자의 approval/rejection을 기록합니다.
     */
    public Map<String, Object> submitApprovalDecision(
            String boardId,
            String reviewer,
            String decision, // APPROVED, REJECTED, REQUEST_CHANGES
            String reason) {

        Map<String, Object> approval = new HashMap<>();
        approval.put("id", "approval-" + System.nanoTime());
        approval.put("reviewer", reviewer);
        approval.put("decision", decision);
        approval.put("reason", reason);
        approval.put("timestamp", Instant.now().toEpochMilli());

        return approval;
    }

    /**
     * revision iteration을 생성합니다.
     */
    public Map<String, Object> createRevisionIteration(
            String boardId,
            String developerName,
            String revisedHtmlContent,
            List<String> changesDescription) {

        Map<String, Object> revision = new HashMap<>();
        revision.put("id", "revision-" + System.nanoTime());
        revision.put("boardId", boardId);
        revision.put("developer", developerName);
        revision.put("changesDescription", changesDescription);
        revision.put("htmlFingerprint", computeFingerprint(revisedHtmlContent));
        revision.put("createdAt", Instant.now().toEpochMilli());
        revision.put("status", "RESUBMITTED_FOR_REVIEW");

        return revision;
    }

    /**
     * 전체 approval workflow 상태를 조회합니다.
     */
    public Map<String, Object> getApprovalWorkflowStatus(String boardId) {
        Map<String, Object> status = new HashMap<>();
        status.put("boardId", boardId);
        status.put("currentPhase", "REVIEW_IN_PROGRESS");
        status.put("reviewersNeeded", List.of("Designer", "Developer", "QA"));
        status.put("reviewersApproved", new ArrayList<>());
        status.put("blockers", new ArrayList<>());
        status.put("minors", new ArrayList<>());
        status.put("revisionCount", 0);
        status.put("lastUpdated", Instant.now().toEpochMilli());

        return status;
    }

    /**
     * Approval workflow를 완료 처리합니다.
     */
    public Map<String, Object> completeApprovalWorkflow(
            String boardId,
            String deployTarget) {

        Map<String, Object> completion = new HashMap<>();
        completion.put("boardId", boardId);
        completion.put("status", "APPROVED_FOR_DEPLOYMENT");
        completion.put("deployTarget", deployTarget);
        completion.put("completedAt", Instant.now().toEpochMilli());
        completion.put("nextStep", "Deploy to " + deployTarget);

        return completion;
    }

    /**
     * Approval workflow를 거부 처리합니다.
     */
    public Map<String, Object> rejectApprovalWorkflow(
            String boardId,
            String rejectionReason) {

        Map<String, Object> rejection = new HashMap<>();
        rejection.put("boardId", boardId);
        rejection.put("status", "REJECTED");
        rejection.put("rejectionReason", rejectionReason);
        rejection.put("rejectedAt", Instant.now().toEpochMilli());
        rejection.put("nextStep", "Developer must address rejection reason and resubmit");

        return rejection;
    }

    /**
     * Figma comment thread를 생성합니다.
     */
    public Map<String, Object> createFigmaCommentThread(
            String figmaNodeId,
            String initiator,
            String message) {

        Map<String, Object> thread = new HashMap<>();
        thread.put("id", "thread-" + System.nanoTime());
        thread.put("figmaNodeId", figmaNodeId);
        thread.put("initiator", initiator);
        thread.put("message", message);
        thread.put("createdAt", Instant.now().toEpochMilli());
        thread.put("replies", new ArrayList<>());
        thread.put("resolved", false);

        return thread;
    }

    // ===== Helper Methods =====

    private String computeFingerprint(String content) {
        return String.valueOf(content.hashCode());
    }
}
