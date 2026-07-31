package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationFailure;

import java.util.List;

/**
 * Verifier 1개의 검증 결과. 명세서 §10.7.
 *
 * <p>{@link #summaryFragment}는 결과 VO의 {@code validationSummary}에 실행 순서대로 이어붙는
 * 조각이다 — null이면 아무것도 덧붙이지 않는다.
 */
public record VerificationResult(String summaryFragment, List<GenerationFailure> failures) {

    public VerificationResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
    }

    public static VerificationResult summary(String summaryFragment) {
        return new VerificationResult(summaryFragment, List.of());
    }

    public static VerificationResult none() {
        return new VerificationResult(null, List.of());
    }
}
