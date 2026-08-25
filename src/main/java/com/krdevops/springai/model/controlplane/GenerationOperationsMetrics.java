package com.krdevops.springai.model.controlplane;

import java.time.Instant;
import java.util.Map;

/** 저장 Row 수와 실제 Operation 수를 분리해 보여주는 운영 조회 결과. */
public record GenerationOperationsMetrics(
        Instant generatedAt,
        PipelineMetrics crud,
        PipelineMetrics thymeleafMigration) {

    public record PipelineMetrics(
            long totalRows,
            long distinctOperations,
            Map<String, Long> allRevisionStatusCounts,
            Map<String, Long> latestStatusCounts,
            Map<String, Long> callerTypeCounts,
            Map<String, Long> actorCounts,
            Map<String, Long> environmentCounts,
            Map<String, Long> projectCounts,
            Map<String, Long> screenCounts) {
        public PipelineMetrics {
            allRevisionStatusCounts = copy(allRevisionStatusCounts);
            latestStatusCounts = copy(latestStatusCounts);
            callerTypeCounts = copy(callerTypeCounts);
            actorCounts = copy(actorCounts);
            environmentCounts = copy(environmentCounts);
            projectCounts = copy(projectCounts);
            screenCounts = copy(screenCounts);
        }

        private static Map<String, Long> copy(Map<String, Long> value) {
            return value == null ? Map.of() : Map.copyOf(value);
        }
    }
}
