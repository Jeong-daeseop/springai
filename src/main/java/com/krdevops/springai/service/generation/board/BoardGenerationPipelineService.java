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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        String pipelineId = java.util.UUID.randomUUID().toString();
        log.info("[pipeline:{}] BOARD Planner 시작: table={}, domain={}", pipelineId,
                command.mainTable(), command.domain());
        BoardGenerationPlan plan = planner.plan(command);
        if (plan.failed()) {
            log.warn("[pipeline:{}] BOARD Planner 실패: {}", pipelineId, plan.failure().summary());
            return new BoardPipelineResult(plan, null, java.util.List.of(), plan.failure().summary(), "");
        }
        log.info("[pipeline:{}] BOARD Planner 완료", pipelineId);
        log.info("[pipeline:{}] BOARD Renderer 시작", pipelineId);
        RenderedGenerationPlan rendered = renderer.render(plan, command);
        log.info("[pipeline:{}] BOARD Renderer 완료: files={}", pipelineId, rendered.files().size());
        log.info("[pipeline:{}] BOARD WRITE Executor 시작", pipelineId);
        GenerationExecution execution = executor.execute(rendered);
        log.info("[pipeline:{}] BOARD WRITE Executor 완료: success={}, failed={}", pipelineId,
                execution.succeededNames().size(), execution.failedFiles().size());
        if (processorRunner == null || rendered.context() == null) {
            log.info("[pipeline:{}] BOARD Pipeline 종료: legacy processor boundary", pipelineId);
            return new BoardPipelineResult(plan, execution);
        }
        GenerationBlueprint blueprint = new GenerationBlueprint(
                rendered.context(),
                rendered.files().stream().map(file -> new FileBlueprint(
                        file.layerKey(), file.displayName(), file.targetPath(), null)).toList(),
                rendered.processors(), rendered.warnings());
        GenerationProcessingContext afterWrite = GenerationProcessingContext.beforeRender(blueprint)
                .withExecution(rendered, execution);
        log.info("[pipeline:{}] BOARD POST_WRITE Processor 시작", pipelineId);
        var processorResult = processorRunner.run(GenerationStage.POST_WRITE, rendered.processors(), afterWrite);
        log.info("[pipeline:{}] BOARD POST_WRITE Processor 완료: failures={}", pipelineId,
                processorResult.failures().size());
        String validation = "";
        java.util.List<GenerationFailure> failures = new java.util.ArrayList<>(processorResult.failures());
        if (verifierRunner != null) {
            log.info("[pipeline:{}] BOARD Contract Verifier 시작", pipelineId);
            var verification = verifierRunner.run(afterWrite);
            validation = verification.summary();
            failures.addAll(verification.failures());
            log.info("[pipeline:{}] BOARD Contract Verifier 완료: failures={}", pipelineId,
                    verification.failures().size());
        }
        log.info("[pipeline:{}] BOARD History Recorder 시작", pipelineId);
        String history = historyRecorder == null ? "" : historyRecorder.record(afterWrite).summary();
        log.info("[pipeline:{}] BOARD History Recorder 완료", pipelineId);
        log.info("[pipeline:{}] BOARD Result Assembler 입력 준비 완료", pipelineId);
        return new BoardPipelineResult(plan, execution, failures, validation, history);
    }
}
