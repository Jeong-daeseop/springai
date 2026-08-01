package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationHistoryRecorder;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Board Planner → Renderer → Executor 연결 서비스.
 *
 * <p>계획 실패 시 저장을 시도하지 않고, 계획 성공 시 파일별 저장 후 POST_WRITE Processor를
 * 선언 순서대로 실행한다.
 */
@Service
public class BoardGenerationPipelineService {

    private final BoardGenerationPlanner planner;
    private final BoardGenerationRenderer renderer;
    private final GenerationExecutor executor;
    private final GenerationProcessorRunner processorRunner;
    private final GenerationVerifierRunner verifierRunner;
    private final GenerationHistoryRecorder historyRecorder;

    public BoardGenerationPipelineService(BoardGenerationPlanner planner,
                                          BoardGenerationRenderer renderer,
                                          GenerationExecutor executor) {
        this(planner, renderer, executor, null);
    }

    public BoardGenerationPipelineService(BoardGenerationPlanner planner,
                                          BoardGenerationRenderer renderer,
                                          GenerationExecutor executor,
                                          GenerationProcessorRunner processorRunner) {
        this(planner, renderer, executor, processorRunner, null, null);
    }

    @Autowired
    public BoardGenerationPipelineService(BoardGenerationPlanner planner,
                                          BoardGenerationRenderer renderer,
                                          GenerationExecutor executor,
                                          GenerationProcessorRunner processorRunner,
                                          GenerationVerifierRunner verifierRunner,
                                          GenerationHistoryRecorder historyRecorder) {
        this.planner = planner;
        this.renderer = renderer;
        this.executor = executor;
        this.processorRunner = processorRunner;
        this.verifierRunner = verifierRunner;
        this.historyRecorder = historyRecorder;
    }

    public BoardPipelineResult execute(BoardGenerationCommand command) {
        BoardGenerationPlan plan = planner.plan(command);
        if (plan.failed()) {
            return new BoardPipelineResult(plan, null, java.util.List.of(), plan.failure().summary(), "");
        }
        RenderedGenerationPlan rendered = renderer.render(plan, command);
        GenerationExecution execution = executor.execute(rendered);
        if (processorRunner == null || rendered.context() == null) {
            return new BoardPipelineResult(plan, execution);
        }
        GenerationBlueprint blueprint = new GenerationBlueprint(
                rendered.context(),
                rendered.files().stream().map(file -> new FileBlueprint(
                        file.layerKey(), file.displayName(), file.targetPath(), null)).toList(),
                rendered.processors(), rendered.warnings());
        GenerationProcessingContext afterWrite = GenerationProcessingContext.beforeRender(blueprint)
                .withExecution(rendered, execution);
        var processorResult = processorRunner.run(GenerationStage.POST_WRITE, rendered.processors(), afterWrite);
        String validation = "";
        java.util.List<GenerationFailure> failures = new java.util.ArrayList<>(processorResult.failures());
        if (verifierRunner != null) {
            var verification = verifierRunner.run(afterWrite);
            validation = verification.summary();
            failures.addAll(verification.failures());
        }
        String history = historyRecorder == null ? "" : historyRecorder.record(afterWrite).summary();
        return new BoardPipelineResult(plan, execution, failures, validation, history);
    }
}
