package com.krdevops.springai.service.generation.model;

import java.util.List;

/**
 * Blueprint가 선언하는 Processor 1개의 실행 계획. 명세서 §10.6.
 *
 * <p>실행 순서는 {@code stage} → {@code order} → {@code processorId} 순으로 결정하며
 * Spring Bean 주입 순서에 의존하지 않는다.
 */
public record ProcessorStep(
        String processorId,
        GenerationStage stage,
        int order,
        FailurePolicy failurePolicy,
        List<String> dependsOn
) {
    public ProcessorStep {
        dependsOn = dependsOn == null ? List.of() : List.copyOf(dependsOn);
    }

    public ProcessorStep(String processorId, GenerationStage stage, int order, FailurePolicy failurePolicy) {
        this(processorId, stage, order, failurePolicy, List.of());
    }
}
