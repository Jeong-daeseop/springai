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
import lombok.extern.slf4j.Slf4j;
import java.util.List;

/** Master/Detail Planner → Renderer → 공통 Executor 연결 서비스. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MasterDetailGenerationPipelineService {

    private final MasterDetailGenerationPlanner planner;
    private final MasterDetailGenerationRenderer renderer;
    private final GenerationExecutor executor;
    private final GenerationProcessorRunner processorRunner;
    private final GenerationVerifierRunner verifierRunner;
    private final GenerationHistoryRecorder historyRecorder;

    public MasterDetailPipelineResult execute(MasterDetailGenerationCommand command) {
        String pipelineId = java.util.UUID.randomUUID().toString();
        log.info("[pipeline:{}] MASTER_DETAIL Planner 시작: master={}, detail={}, domain={}", pipelineId,
                command.masterTable(), command.detailTable(), command.domain());
        MasterDetailGenerationPlan plan = planner.plan(command);
        if (plan.failed()) {
            log.warn("[pipeline:{}] MASTER_DETAIL Planner 실패: {}", pipelineId, plan.failure().summary());
            return new MasterDetailPipelineResult(plan, null);
        }
        log.info("[pipeline:{}] MASTER_DETAIL Planner 완료", pipelineId);
        log.info("[pipeline:{}] MASTER_DETAIL Renderer 시작", pipelineId);
        var rendered = renderer.render(plan, command);
        log.info("[pipeline:{}] MASTER_DETAIL Renderer 완료: files={}", pipelineId, rendered.files().size());
        log.info("[pipeline:{}] MASTER_DETAIL WRITE Executor 시작", pipelineId);
        GenerationExecution execution = executor.execute(rendered);
        log.info("[pipeline:{}] MASTER_DETAIL WRITE Executor 완료: success={}, failed={}", pipelineId,
                execution.succeededNames().size(), execution.failedFiles().size());
        GenerationBlueprint blueprint = new GenerationBlueprint(rendered.context(),
                rendered.files().stream().map(file -> new FileBlueprint(
                        file.layerKey(), file.displayName(), file.targetPath(), null)).toList(),
                rendered.processors(), rendered.warnings());
        GenerationProcessingContext afterWrite = GenerationProcessingContext.beforeRender(blueprint)
                .withExecution(rendered, execution);
        log.info("[pipeline:{}] MASTER_DETAIL POST_WRITE Processor 시작", pipelineId);
        var processorRun = processorRunner.run(GenerationStage.POST_WRITE, rendered.processors(), afterWrite);
        log.info("[pipeline:{}] MASTER_DETAIL POST_WRITE Processor 완료: failures={}", pipelineId,
                processorRun.failures().size());
        List<GenerationFailure> failures = new java.util.ArrayList<>(processorRun.failures());
        String validation = "";
        if (verifierRunner != null) {
            log.info("[pipeline:{}] MASTER_DETAIL Contract Verifier 시작", pipelineId);
            var verification = verifierRunner.run(afterWrite);
            validation = verification.summary();
            failures.addAll(verification.failures());
            log.info("[pipeline:{}] MASTER_DETAIL Contract Verifier 완료: failures={}", pipelineId,
                    verification.failures().size());
        }
        log.info("[pipeline:{}] MASTER_DETAIL History Recorder 시작", pipelineId);
        String history = historyRecorder == null ? "" : historyRecorder.record(afterWrite).summary();
        log.info("[pipeline:{}] MASTER_DETAIL History Recorder 완료", pipelineId);
        log.info("[pipeline:{}] MASTER_DETAIL Result Assembler 입력 준비 완료", pipelineId);
        return new MasterDetailPipelineResult(plan, execution, failures, validation, history);
    }
}
