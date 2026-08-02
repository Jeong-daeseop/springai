package com.krdevops.springai.model.contract;

/**
 * Figma와 Thymeleaf 두 흐름이 같은 의미로 참조하는 디자인 시스템 스냅샷 좌표.
 * {@code layoutPolicyVersion}은 아직 {@code PlatformLayoutPolicy}가 구현되지 않은 화면에서는
 * {@code null}일 수 있으나, 값이 있으면 반드시 공백이 아니어야 한다(§3.1, R0-027 선행 준비).
 */
public record DesignSystemSnapshotRef(
        @jakarta.validation.constraints.NotBlank String profileId,
        @jakarta.validation.constraints.NotBlank String profileVersion,
        @jakarta.validation.constraints.NotBlank String registryVersion,
        String layoutPolicyVersion
) {
    public DesignSystemSnapshotRef {
        if (profileId == null || profileId.isBlank()) {
            throw new IllegalArgumentException("profileId는 필수입니다.");
        }
        if (profileVersion == null || profileVersion.isBlank()) {
            throw new IllegalArgumentException("profileVersion은 필수입니다.");
        }
        if (registryVersion == null || registryVersion.isBlank()) {
            throw new IllegalArgumentException("registryVersion은 필수입니다.");
        }
        if (layoutPolicyVersion != null && layoutPolicyVersion.isBlank()) {
            throw new IllegalArgumentException("layoutPolicyVersion은 지정 시 공백일 수 없습니다.");
        }
    }
}
