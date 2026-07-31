package com.krdevops.springai.service.generation.model;

import java.util.List;

/** Renderer 산출물 — Source 문자열까지 채워진 실행 직전 계획. 명세서 §10.4. */
public record RenderedGenerationPlan(
        GenerationContext context,
        List<RenderedFilePlan> files,
        List<ProcessorStep> processors,
        List<GenerationWarning> warnings
) {
    public RenderedGenerationPlan {
        files = files == null ? List.of() : List.copyOf(files);
        processors = processors == null ? List.of() : List.copyOf(processors);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
