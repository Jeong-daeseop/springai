package com.krdevops.springai.model.designsystem;

import java.util.Map;

/** 논리 타입과 Published Figma Library의 실제 Component/Variable을 연결한다(09번 §3.4). */
public record ComponentRegistry(
        String profileId,
        String profileVersion,
        String registryVersion,
        LibraryRef library,
        Map<String, ComponentRegistryEntry> components,
        Map<String, VariableRegistryEntry> variables
) {
    public static final String SCHEMA_VERSION = "component-registry-v1";

    public ComponentRegistry {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId는 필수입니다.");
        }
        if (profileVersion == null || profileVersion.isBlank()) {
            throw new IllegalArgumentException("profileVersion은 필수입니다.");
        }
        if (registryVersion == null || registryVersion.isBlank()) {
            throw new IllegalArgumentException("registryVersion은 필수입니다.");
        }
        components = components == null ? Map.of() : Map.copyOf(components);
        variables = variables == null ? Map.of() : Map.copyOf(variables);
    }

    /** R1 호환 생성자. Variable Registry가 없던 v1 초기 JSON/Java 호출을 그대로 허용한다. */
    public ComponentRegistry(
            String profileId,
            String profileVersion,
            String registryVersion,
            LibraryRef library,
            Map<String, ComponentRegistryEntry> components
    ) {
        this(profileId, profileVersion, registryVersion, library, components, Map.of());
    }

    public record LibraryRef(String fileKey, String name) {}
}
