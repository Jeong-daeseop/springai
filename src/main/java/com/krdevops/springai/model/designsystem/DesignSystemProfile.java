package com.krdevops.springai.model.designsystem;

import java.util.Map;

/** 사용할 디자인 시스템의 식별자·버전·Registry 연결 정보(09번 §3.3). */
public record DesignSystemProfile(
        String id,
        String name,
        String version,
        String registryVersion,
        String libraryFileKey,
        Status status,
        Map<String, ComponentBinding> components,
        Map<String, VariableBinding> variables
) {
    public DesignSystemProfile {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id는 필수입니다.");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version은 필수입니다.");
        }
        status = status == null ? Status.DRAFT : status;
        components = components == null ? Map.of() : Map.copyOf(components);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    /** DRAFT/IN_REVIEW/APPROVED/REJECTED는 Preview 검토 상태(R3-023), PUBLISHED는 실제 Library Publish+Registry 동기화 완료 후 상태다. */
    public enum Status { DRAFT, IN_REVIEW, APPROVED, REJECTED, PUBLISHED }
}
