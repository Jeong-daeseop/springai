package com.krdevops.springai.model.operation;

import java.time.Instant;

/** 영속 Operation 상태 전이 감사 이벤트. */
public record OperationEvent(String eventId, String operationId, String operationType,
                             int revision, String fromStatus, String toStatus,
                             String eventType, String actor, String correlationId,
                             String payloadHash, Instant occurredAt) {
    public OperationEvent {
        if (eventId == null || eventId.isBlank() || operationId == null || operationId.isBlank()) {
            throw new IllegalArgumentException("eventId와 operationId는 필수입니다.");
        }
        operationType = operationType == null ? "UNKNOWN" : operationType;
        eventType = eventType == null ? "STATE_CHANGED" : eventType;
        actor = actor == null || actor.isBlank() ? "system" : actor;
        correlationId = correlationId == null || correlationId.isBlank() ? eventId : correlationId;
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}
