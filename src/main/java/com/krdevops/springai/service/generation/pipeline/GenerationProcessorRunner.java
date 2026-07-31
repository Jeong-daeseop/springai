package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.FailurePolicy;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.ProcessorStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blueprint가 선언한 {@link ProcessorStep}을 stage → order → processorId 순으로 실행한다.
 * 명세서 §10.6.
 *
 * <p>Spring Bean 주입 순서에 의존하지 않는다 — 정렬 키는 전적으로 Blueprint의 선언값이다.
 */
@Slf4j
@Component
public class GenerationProcessorRunner {

    private final Map<String, GenerationStageProcessor> processorsById;

    public GenerationProcessorRunner(List<GenerationStageProcessor> processors) {
        this.processorsById = processors.stream().collect(LinkedHashMap::new,
                (map, processor) -> map.put(processor.id(), processor), Map::putAll);
    }

    public ProcessorRunResult run(
            GenerationStage stage, List<ProcessorStep> steps, GenerationProcessingContext context) {

        List<GenerationFailure> failures = new ArrayList<>();
        Set<String> failedIds = new HashSet<>();

        for (ProcessorStep step : orderedStepsFor(stage, steps)) {
            if (step.dependsOn().stream().anyMatch(failedIds::contains)) {
                log.info("[pipeline] 선행 Processor 실패로 건너뜀: {}", step.processorId());
                continue;
            }
            GenerationStageProcessor processor = processorsById.get(step.processorId());
            if (processor == null) {
                throw new IllegalStateException(
                        "등록되지 않은 Processor id입니다: " + step.processorId());
            }
            if (!processor.supports(context.context())) {
                continue;
            }

            ProcessorResult result = processor.process(context);
            failures.addAll(result.failures());
            if (result.success()) {
                continue;
            }
            if (step.failurePolicy() == FailurePolicy.STOP) {
                return new ProcessorRunResult(true, result.failureSummary(), List.copyOf(failures));
            }
            if (step.failurePolicy() == FailurePolicy.SKIP_DEPENDENTS) {
                failedIds.add(step.processorId());
            }
        }
        return new ProcessorRunResult(false, null, List.copyOf(failures));
    }

    private List<ProcessorStep> orderedStepsFor(GenerationStage stage, List<ProcessorStep> steps) {
        return steps.stream()
                .filter(step -> step.stage() == stage)
                .sorted(Comparator.comparingInt(ProcessorStep::order)
                        .thenComparing(ProcessorStep::processorId))
                .toList();
    }

    /** 한 Stage의 Processor 실행 결과 — {@code stopped}면 Pipeline 전체를 중단한다. */
    public record ProcessorRunResult(boolean stopped, String stopSummary, List<GenerationFailure> failures) {
    }
}
