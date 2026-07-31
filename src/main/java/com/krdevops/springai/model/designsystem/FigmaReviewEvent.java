package com.krdevops.springai.model.designsystem;

import java.time.LocalDateTime;

/** Preview 검토·승인(R1-023)과 Library Publish·Registry 동기화(R1-024) 이력 한 건. */
public record FigmaReviewEvent(
        String id,
        TargetType targetType,
        String targetId,
        String targetVersion,
        EventType eventType,
        String status,
        String actor,
        String comment,
        LocalDateTime occurredAt
) {
    public FigmaReviewEvent {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id는 필수입니다.");
        }
        if (targetType == null || targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetType/targetId는 필수입니다.");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType은 필수입니다.");
        }
        occurredAt = occurredAt == null ? LocalDateTime.now() : occurredAt;
    }

    public enum TargetType { DESIGN_SYSTEM_PROFILE, FIGMA_SCREEN_SPEC }

    public enum EventType { REVIEW, APPROVAL, REJECTION, PUBLISH, REGISTRY_SYNC }
}
