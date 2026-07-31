package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;

/**
 * Processor/Verifier/HistoryRecorder가 받는 실행 문맥. 명세서 §10.6.
 *
 * <p>{@link #renderedPlan}은 RENDER 이전 단계에서, {@link #execution}은 WRITE 이전 단계에서
 * null이다 — 해당 단계의 Processor는 이 두 값을 참조하지 않는다.
 */
public record GenerationProcessingContext(
        GenerationContext context,
        GenerationBlueprint blueprint,
        RenderedGenerationPlan renderedPlan,
        GenerationExecution execution
) {
    public static GenerationProcessingContext beforeRender(GenerationBlueprint blueprint) {
        return new GenerationProcessingContext(blueprint.context(), blueprint, null, null);
    }

    public GenerationProcessingContext withExecution(
            RenderedGenerationPlan renderedPlan, GenerationExecution execution) {
        return new GenerationProcessingContext(context, blueprint, renderedPlan, execution);
    }
}
