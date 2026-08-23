package com.krdevops.springai.model.event;
import java.time.Instant;
public record DesignSystemEvent(String eventId, EventType type, String artifactId, String contentHash, Instant occurredAt) { public DesignSystemEvent { if(eventId==null||eventId.isBlank()||type==null||artifactId==null||artifactId.isBlank()||contentHash==null||contentHash.isBlank()||occurredAt==null) throw new IllegalArgumentException("Event 값이 올바르지 않습니다."); } public enum EventType { SNAPSHOT_PUBLISHED, COMPONENT_BLOCKED, TOKEN_CHANGED, MAPPING_CHANGED } }
