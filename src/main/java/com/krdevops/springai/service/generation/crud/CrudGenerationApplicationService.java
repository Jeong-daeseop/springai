package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.generation.api.GenerateCrudProjectUseCase;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.generation.pipeline.GenerationHistoryRecorder;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import com.krdevops.springai.service.generation.pipeline.HistoryRecordResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * CRUD 생성 Pipeline 조율 — Planner → PRE_WRITE → Renderer → Executor(WRITE) → POST_WRITE →
 * PRE_VERIFY/VERIFY → HISTORY.
 *
 * <p><b>PRE_WRITE가 RENDER보다 먼저인 이유</b>: {@code GenerationStage} enum은 {@code RENDER}를
 * {@code PRE_WRITE}보다 앞에 두지만, WP-0의 {@code CrudOrchestrationProcessorOrderTest}가 실측한
 * 실제 동작은 CSS 보강(PRE_WRITE)이 템플릿 렌더링보다 먼저다. {@code ORT-PRN-005}(기존 동작 보존)가
 * enum 배치보다 우선하므로 실제 순서를 따른다 — CSS 보강 실패 시 렌더링조차 시도하지 않는
 * 기존 동작이 그대로 유지된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrudGenerationApplicationService implements GenerateCrudProjectUseCase {

    private final CrudGenerationPlanner planner;
    private final CrudGenerationRenderer renderer;
    private final GenerationExecutor executor;
    private final GenerationProcessorRunner processorRunner;
    private final GenerationVerifierRunner verifierRunner;
    private final GenerationHistoryRecorder historyRecorder;
    private final CrudGenerationResultAssembler assembler;

    @Override
    public CrudOrchestrationResult execute(CrudGenerationCommand command) {
        CrudGenerationPlan plan = planner.plan(command);
        if (plan.failed()) {
            return assembler.assemble(command, plan.failure());
        }
        GenerationBlueprint blueprint = plan.blueprint();

        GenerationProcessorRunner.ProcessorRunResult preWrite = processorRunner.run(
                GenerationStage.PRE_WRITE, blueprint.processors(),
                GenerationProcessingContext.beforeRender(blueprint));
        if (preWrite.stopped()) {
            return assembler.assembleStopped(command, plan, preWrite.stopSummary(), preWrite.failures());
        }

        RenderedGenerationPlan renderedPlan = renderer.render(blueprint);
        GenerationExecution execution = executor.execute(renderedPlan);
        List<GenerationFailure> failures = new ArrayList<>(preWrite.failures());
        failures.addAll(execution.failedFiles());

        GenerationProcessingContext afterWrite = GenerationProcessingContext
                .beforeRender(blueprint)
                .withExecution(renderedPlan, execution);

        failures.addAll(processorRunner
                .run(GenerationStage.POST_WRITE, blueprint.processors(), afterWrite)
                .failures());

        GenerationVerifierRunner.VerificationRunResult verification = verifierRunner.run(afterWrite);
        failures.addAll(verification.failures());

        HistoryRecordResult history = historyRecorder.record(afterWrite);

        log.info("[pipeline] 완료: successCount={}, failCount={}",
                 execution.succeededFiles().size(), failures.size());
        return assembler.assemble(
                command, plan, execution, failures, verification.summary(), history.summary());
    }
}
