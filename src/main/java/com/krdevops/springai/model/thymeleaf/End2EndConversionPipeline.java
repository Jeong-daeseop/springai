package com.krdevops.springai.model.thymeleaf;

import java.time.Instant;
import java.util.List;

/**
 * I-2~I-7 전체 파이프라인 결과.
 * JSP 분석부터 프로젝트 적용까지 모든 단계의 결과를 통합.
 */
public record End2EndConversionPipeline(
        String pipelineId,
        String sourceJspPath,
        LegacyScreenAnalysis screenAnalysis,
        ThymeleafBindingContract bindingContract,
        String renderedHtml,
        ThymeleafConversionOperation appliedOperation,
        ProjectApplicationResult projectDeployment,
        PipelineStatus status,
        List<String> issues,
        PipelineMetrics metrics
) {
    public enum PipelineStatus {
        SUCCESS,
        ANALYSIS_FAILED,
        RENDERING_FAILED,
        APPLY_FAILED,
        DEPLOYMENT_FAILED
    }

    public record PipelineMetrics(
            long analysisTimeMs,
            long renderingTimeMs,
            long applyTimeMs,
            long deploymentTimeMs,
            long totalTimeMs
    ) {
        public long getTotalTimeSeconds() {
            return totalTimeMs / 1000;
        }
    }

    public boolean isSuccessful() {
        return status == PipelineStatus.SUCCESS && issues.isEmpty();
    }
}
