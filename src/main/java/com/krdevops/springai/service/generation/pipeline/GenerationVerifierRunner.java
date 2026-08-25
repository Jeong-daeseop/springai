package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.controlplane.GenerationVerificationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class GenerationVerifierRunner {

    private final List<GenerationVerifier> verifiers;
    private final List<GenerationVerificationObserver> observers;

    public GenerationVerifierRunner(List<GenerationVerifier> verifiers) {
        this(verifiers, List.of());
    }

    @Autowired
    public GenerationVerifierRunner(List<GenerationVerifier> verifiers,
                                    List<GenerationVerificationObserver> observers) {
        this.verifiers = verifiers.stream()
                .sorted(Comparator.comparing((GenerationVerifier v) -> v.stage().ordinal())
                        .thenComparingInt(GenerationVerifier::order)
                        .thenComparing(GenerationVerifier::id))
                .toList();
        this.observers = List.copyOf(observers == null ? List.of() : observers);
    }

    public VerificationRunResult run(GenerationProcessingContext context) {
        StringBuilder summary = new StringBuilder();
        List<GenerationFailure> failures = new ArrayList<>();
        List<VerifierOutcome> outcomes = new ArrayList<>();

        for (GenerationVerifier verifier : verifiers) {
            if (!verifier.supports(context.context())) {
                continue;
            }
            VerificationResult result = verifier.verify(context);
            outcomes.add(new VerifierOutcome(verifier.id(), verifier.stage(), result));
            if (result.summaryFragment() != null) {
                summary.append(result.summaryFragment());
            }
            failures.addAll(result.failures());
        }
        VerificationRunResult runResult = new VerificationRunResult(
                summary.toString(), List.copyOf(failures), List.copyOf(outcomes));
        observers.forEach(observer -> {
            try {
                observer.onCompleted(context, runResult);
            } catch (RuntimeException exception) {
                // 병행 기록 장애가 기존 생성·검증 결과를 바꾸지 않도록 한다.
                log.error("[pipeline] 검증 증적 저장 실패: observer={}",
                        observer.getClass().getSimpleName(), exception);
            }
        });
        return runResult;
    }

    public record VerificationRunResult(String summary, List<GenerationFailure> failures,
                                        List<VerifierOutcome> outcomes) {
        public VerificationRunResult(String summary, List<GenerationFailure> failures) {
            this(summary, failures, List.of());
        }

        public VerificationRunResult {
            failures = failures == null ? List.of() : List.copyOf(failures);
            outcomes = outcomes == null ? List.of() : List.copyOf(outcomes);
        }
    }

    public record VerifierOutcome(String verifierId, GenerationStage stage, VerificationResult result) { }
}
