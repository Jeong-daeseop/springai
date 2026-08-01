package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import java.util.List;

/** Master/Detail Planner·Renderer·Executor 단계 결과. */
public record MasterDetailPipelineResult(MasterDetailGenerationPlan plan,
                                         GenerationExecution execution,
                                         List<GenerationFailure> failures,
                                         String validationSummary,
                                         String historySummary) {
    public MasterDetailPipelineResult(MasterDetailGenerationPlan plan, GenerationExecution execution) {
        this(plan, execution, List.of(), "", "");
    }
    public boolean planned() { return plan != null && !plan.failed(); }
}
