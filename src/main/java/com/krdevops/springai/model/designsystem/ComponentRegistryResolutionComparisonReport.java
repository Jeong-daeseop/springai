package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.service.designsystem.ComponentRegistryResolutionComparisonService;
import java.time.Instant;
import java.util.List;

/** 관찰 모드 Legacy/Resolved Registry 비교 결과의 불변 증적. */
public record ComponentRegistryResolutionComparisonReport(
        String reportId,
        String profileId,
        String legacyRegistryVersion,
        String resolvedRegistryVersion,
        Instant comparedAt,
        boolean identical,
        List<ComponentRegistryResolutionComparisonService.Difference> differences
) {
    public ComponentRegistryResolutionComparisonReport {
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
}
