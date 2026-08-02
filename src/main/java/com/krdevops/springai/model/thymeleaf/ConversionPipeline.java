package com.krdevops.springai.model.thymeleaf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * I-7A: Thymeleaf 변환 파이프라인 상태.
 * JSP → Thymeleaf 전체 흐름을 추적합니다.
 */
public record ConversionPipeline(
    String pipelineId,
    String jspFilePath,
    PipelinePhase currentPhase,
    List<PipelineStage> completedStages,
    Map<String, Object> conversionResults,
    List<String> errors,
    long startedAt,
    long completedAt) {

    public ConversionPipeline {
        if (completedStages == null) {
            completedStages = new ArrayList<>();
        }
        if (conversionResults == null) {
            conversionResults = Map.of();
        }
        if (errors == null) {
            errors = new ArrayList<>();
        }
    }

    public enum PipelinePhase {
        INITIALIZED,
        SCREEN_DECISION,
        SKELETON_GENERATION,
        RESPONSIVE_TRANSFORM,
        VALIDATION,
        FIGMA_SYNC,
        COMPLETED,
        FAILED
    }

    public record PipelineStage(
        String stageName,
        PipelinePhase phase,
        long startedAt,
        long completedAt,
        boolean success,
        String output) {}

    public boolean isSuccessful() {
        return currentPhase == PipelinePhase.COMPLETED && errors.isEmpty();
    }

    public boolean canProceedToNext() {
        return !isSuccessful() && !currentPhase.equals(PipelinePhase.FAILED);
    }
}
