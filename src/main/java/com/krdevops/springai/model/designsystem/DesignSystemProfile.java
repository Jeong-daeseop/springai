package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.contract.DefaultLayoutPolicy;

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
        Map<String, VariableBinding> variables,
        String layoutPolicyVersion,
        DefaultLayoutPolicy layoutPolicy
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

    /** R1-015 이전 8-필드 호출자 호환 — layoutPolicy 미지정 Profile을 생성한다. */
    public DesignSystemProfile(
            String id,
            String name,
            String version,
            String registryVersion,
            String libraryFileKey,
            Status status,
            Map<String, ComponentBinding> components,
            Map<String, VariableBinding> variables
    ) {
        this(id, name, version, registryVersion, libraryFileKey, status, components, variables, null, null);
    }

    /** DRAFT/IN_REVIEW/APPROVED/REJECTED는 Preview 검토 상태(R3-023), PUBLISHED는 실제 Library Publish+Registry 동기화 완료 후 상태다. */
    public enum Status { DRAFT, IN_REVIEW, APPROVED, REJECTED, PUBLISHED }
}
