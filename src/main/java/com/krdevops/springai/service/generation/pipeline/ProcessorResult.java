package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationFailure;

import java.util.List;

/**
 * Processor 1개의 실행 결과. 명세서 §10.6.
 *
 * <p>실패해도 그것이 Pipeline을 멈추는지는 Processor가 아니라 {@code ProcessorStep}의
 * {@code FailurePolicy}가 결정한다. {@link #failureSummary}는 {@code STOP}으로 중단될 때
 * 결과 VO의 {@code validationSummary}에 들어갈 요약 문자열이다(예: {@code "CSS 보강 실패"}).
 */
public record ProcessorResult(boolean success, String failureSummary, List<GenerationFailure> failures) {

    public ProcessorResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static ProcessorResult ok() {
        return new ProcessorResult(true, null, List.of());
    }

    public static ProcessorResult failed(List<GenerationFailure> failures) {
        return new ProcessorResult(false, null, failures);
    }

    public static ProcessorResult failed(String failureSummary, List<GenerationFailure> failures) {
        return new ProcessorResult(false, failureSummary, failures);
    }
}
