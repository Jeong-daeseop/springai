package com.krdevops.springai.model.renderer;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Renderer 결과에 적용할 Validation Gate와 심각도 정책의 버전 산출물. */
public record ValidatorProfile(
        String profileId,
        String version,
        String contentHash,
        Status status,
        Map<ValidationGateType, GateSeverity> gatePolicies,
        List<String> requiredEvidence
) {
    public ValidatorProfile {
        if (profileId == null || profileId.isBlank()) throw new IllegalArgumentException("profileId는 필수입니다.");
        if (version == null || version.isBlank()) throw new IllegalArgumentException("version은 필수입니다.");
        contentHash = ContentHashes.requireValid(contentHash);
        if (status == null) throw new IllegalArgumentException("status는 필수입니다.");
        gatePolicies = gatePolicies == null ? Map.of()
                : Map.copyOf(new EnumMap<>(gatePolicies));
        requiredEvidence = requiredEvidence == null ? List.of() : List.copyOf(requiredEvidence);
        if (gatePolicies.isEmpty()) throw new IllegalArgumentException("gatePolicies는 하나 이상이어야 합니다.");
    }

    public enum Status { DRAFT, APPROVED, SUPERSEDED }
}
