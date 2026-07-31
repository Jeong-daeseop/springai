package com.krdevops.springai.model.figma.ops;

import java.time.Instant;

/** R8 운영 대시보드용 누적 품질 지표. */
public record FigmaOperationalMetrics(
        long totalRuns,
        long successfulRuns,
        double successRate,
        double averageDurationMillis,
        long affectedNodeCount,
        long reusedInstanceCount,
        long createdInstanceCount,
        double instanceReuseRate,
        long archivedNodeCount,
        long fallbackCount,
        double fallbackRate,
        long registryMismatchCount,
        long mergeConflictCount,
        long userOverridePreservationFailureCount,
        long previewRejectionCount,
        long previewReviewCount,
        Instant calculatedAt
) {
}
