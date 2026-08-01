package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationHistoryRecorder;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationStage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

/** Master/Detail Planner → Renderer → 공통 Executor 연결 서비스. */
@Service
@RequiredArgsConstructor
public class MasterDetailGenerationPipelineService {

    private final MasterDetailGenerationPlanner planner;
    private final MasterDetailGenerationRenderer renderer;
    private final GenerationExecutor executor;
    private final GenerationProcessorRunner processorRunner;
    private final GenerationVerifierRunner verifierRunner;
    private final GenerationHistoryRecorder historyRecorder;

    public MasterDetailPipelineResult execute(MasterDetailGenerationCommand command) {
        MasterDetailGenerationPlan plan = planner.plan(command);
        if (plan.failed()) {
            return new MasterDetailPipelineResult(plan, null);
        }
        var rendered = renderer.render(plan, command);
        GenerationExecution execution = executor.execute(rendered);
        GenerationBlueprint blueprint = new GenerationBlueprint(rendered.context(),
                rendered.files().stream().map(file -> new FileBlueprint(
                        file.layerKey(), file.displayName(), file.targetPath(), null)).toList(),
                rendered.processors(), rendered.warnings());
        GenerationProcessingContext afterWrite = GenerationProcessingContext.beforeRender(blueprint)
                .withExecution(rendered, execution);
        var processorRun = processorRunner.run(GenerationStage.POST_WRITE, rendered.processors(), afterWrite);
        List<GenerationFailure> failures = new java.util.ArrayList<>(processorRun.failures());
        String validation = "";
        if (verifierRunner != null) {
            var verification = verifierRunner.run(afterWrite);
            validation = verification.summary();
            failures.addAll(verification.failures());
        }
        String history = historyRecorder == null ? "" : historyRecorder.record(afterWrite).summary();
        return new MasterDetailPipelineResult(plan, execution, failures, validation, history);
    }
}
