package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import java.util.List;

/** Board Planner·Renderer·Executor 연결 단계의 결과. */
public record BoardPipelineResult(BoardGenerationPlan plan, GenerationExecution execution,
                                  List<GenerationFailure> processorFailures,
                                  String validationSummary, String historySummary) {
    public BoardPipelineResult(BoardGenerationPlan plan, GenerationExecution execution) {
        this(plan, execution, List.of(), "", "");
    }

    public BoardPipelineResult(BoardGenerationPlan plan, GenerationExecution execution,
                               List<GenerationFailure> processorFailures) {
        this(plan, execution, processorFailures, "", "");
    }

    public BoardPipelineResult {
        processorFailures = processorFailures == null ? List.of() : List.copyOf(processorFailures);
    }
    public boolean planned() {
        return plan != null && !plan.failed();
    }
}
