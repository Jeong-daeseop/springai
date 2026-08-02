package com.krdevops.springai.model.parity;

import java.util.List;
import java.util.Optional;

/**
 * ARCH-0207: {@code DesignParityValidationUseCase} 출력 계약.
 */
public record DesignParityResult(
        DesignParityStatus status,
        Optional<String> evidenceArtifactId,
        List<String> issues) {

    public DesignParityResult {
        evidenceArtifactId = evidenceArtifactId == null ? Optional.empty() : evidenceArtifactId;
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public static DesignParityResult verified(String evidenceArtifactId) {
        return new DesignParityResult(DesignParityStatus.VERIFIED, Optional.of(evidenceArtifactId), List.of());
    }

    public static DesignParityResult of(DesignParityStatus status, String issue) {
        return new DesignParityResult(status, Optional.empty(), List.of(issue));
    }
}
