package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationFailure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 등록된 {@link GenerationVerifier}를 stage → order → id 순으로 실행하고 요약 조각을 이어붙인다.
 *
 * <p>WP-0 {@code CrudOrchestrationProcessorOrderTest}가 실측한 실제 순서
 * (Directory 검증 → Common Contract 감사)를 보존하기 위해 Directory 검증은 {@code PRE_VERIFY},
 * Contract 감사는 {@code VERIFY}에 배정되어 있다 — 명세서 §10.6/§11.1 표의 이름표와는 반대이며,
 * 이는 {@code ORT-PRN-005}(기존 동작 보존)가 표기보다 우선하기 때문이다.
 */
@Component
public class GenerationVerifierRunner {

    private final List<GenerationVerifier> verifiers;

    public GenerationVerifierRunner(List<GenerationVerifier> verifiers) {
        this.verifiers = verifiers.stream()
                .sorted(Comparator.comparing((GenerationVerifier v) -> v.stage().ordinal())
                        .thenComparingInt(GenerationVerifier::order)
                        .thenComparing(GenerationVerifier::id))
                .toList();
    }

    public VerificationRunResult run(GenerationProcessingContext context) {
        StringBuilder summary = new StringBuilder();
        List<GenerationFailure> failures = new ArrayList<>();

        for (GenerationVerifier verifier : verifiers) {
            if (!verifier.supports(context.context())) {
                continue;
            }
            VerificationResult result = verifier.verify(context);
            if (result.summaryFragment() != null) {
                summary.append(result.summaryFragment());
            }
            failures.addAll(result.failures());
        }
        return new VerificationRunResult(summary.toString(), List.copyOf(failures));
    }

    public record VerificationRunResult(String summary, List<GenerationFailure> failures) {
    }
}
