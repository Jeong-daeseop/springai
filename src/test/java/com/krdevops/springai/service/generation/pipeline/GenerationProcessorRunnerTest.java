package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.FailurePolicy;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.model.ProcessorStep;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link GenerationProcessorRunner}의 정렬 규칙과 {@link FailurePolicy} 적용을 검증한다.
 * 실행 순서가 Spring Bean 주입 순서가 아니라 Blueprint의 stage/order 선언을 따르는지가 핵심이다.
 */
class GenerationProcessorRunnerTest {

    private final List<String> calls = new ArrayList<>();

    @Test
    void runsStepsOrderedByOrderThenIdRegardlessOfInjectionOrder() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(
                processor("c", GenerationStage.POST_WRITE),
                processor("a", GenerationStage.POST_WRITE),
                processor("b", GenerationStage.POST_WRITE)));

        runner.run(GenerationStage.POST_WRITE, List.of(
                step("c", GenerationStage.POST_WRITE, 300, FailurePolicy.CONTINUE),
                step("b", GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
                step("a", GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE)),
                context());

        assertThat(calls).containsExactly("a", "b", "c");
    }

    @Test
    void runsOnlyStepsOfRequestedStage() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(
                processor("pre", GenerationStage.PRE_WRITE),
                processor("post", GenerationStage.POST_WRITE)));

        runner.run(GenerationStage.PRE_WRITE, List.of(
                step("pre", GenerationStage.PRE_WRITE, 100, FailurePolicy.CONTINUE),
                step("post", GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE)),
                context());

        assertThat(calls).containsExactly("pre");
    }

    @Test
    void skipsProcessorsThatDoNotSupportContext() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(
                processor("skipped", GenerationStage.POST_WRITE, false, ProcessorResult.ok()),
                processor("ran", GenerationStage.POST_WRITE)));

        runner.run(GenerationStage.POST_WRITE, List.of(
                step("skipped", GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
                step("ran", GenerationStage.POST_WRITE, 200, FailurePolicy.CONTINUE)),
                context());

        assertThat(calls).containsExactly("ran");
    }

    @Test
    void stopPolicyHaltsRemainingStepsAndReportsSummary() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(
                processor("failing", GenerationStage.PRE_WRITE, true,
                        ProcessorResult.failed("CSS 보강 실패",
                                List.of(new GenerationFailure("failing", "styles.css — marker 손상")))),
                processor("later", GenerationStage.PRE_WRITE)));

        GenerationProcessorRunner.ProcessorRunResult result = runner.run(GenerationStage.PRE_WRITE, List.of(
                step("failing", GenerationStage.PRE_WRITE, 100, FailurePolicy.STOP),
                step("later", GenerationStage.PRE_WRITE, 110, FailurePolicy.STOP)),
                context());

        assertThat(result.stopped()).isTrue();
        assertThat(result.stopSummary()).isEqualTo("CSS 보강 실패");
        assertThat(result.failures()).extracting(GenerationFailure::description)
                .containsExactly("styles.css — marker 손상");
        assertThat(calls).containsExactly("failing");
    }

    @Test
    void continuePolicyAccumulatesFailuresAndKeepsGoing() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(
                processor("failing", GenerationStage.POST_WRITE, true,
                        ProcessorResult.failed(List.of(new GenerationFailure("failing", "context-common.xml — 실패")))),
                processor("later", GenerationStage.POST_WRITE)));

        GenerationProcessorRunner.ProcessorRunResult result = runner.run(GenerationStage.POST_WRITE, List.of(
                step("failing", GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE),
                step("later", GenerationStage.POST_WRITE, 200, FailurePolicy.CONTINUE)),
                context());

        assertThat(result.stopped()).isFalse();
        assertThat(result.failures()).hasSize(1);
        assertThat(calls).containsExactly("failing", "later");
    }

    @Test
    void skipDependentsPolicySkipsOnlyDeclaredDependents() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of(
                processor("root", GenerationStage.POST_WRITE, true,
                        ProcessorResult.failed(List.of(new GenerationFailure("root", "실패")))),
                processor("dependent", GenerationStage.POST_WRITE),
                processor("independent", GenerationStage.POST_WRITE)));

        runner.run(GenerationStage.POST_WRITE, List.of(
                new ProcessorStep("root", GenerationStage.POST_WRITE, 100,
                        FailurePolicy.SKIP_DEPENDENTS, List.of()),
                new ProcessorStep("dependent", GenerationStage.POST_WRITE, 200,
                        FailurePolicy.CONTINUE, List.of("root")),
                new ProcessorStep("independent", GenerationStage.POST_WRITE, 300,
                        FailurePolicy.CONTINUE, List.of())),
                context());

        assertThat(calls).containsExactly("root", "independent");
    }

    @Test
    void unknownProcessorIdFailsFast() {
        GenerationProcessorRunner runner = new GenerationProcessorRunner(List.of());

        assertThatThrownBy(() -> runner.run(GenerationStage.POST_WRITE,
                List.of(step("missing", GenerationStage.POST_WRITE, 100, FailurePolicy.CONTINUE)),
                context()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing");
    }

    private static ProcessorStep step(String id, GenerationStage stage, int order, FailurePolicy policy) {
        return new ProcessorStep(id, stage, order, policy);
    }

    private GenerationStageProcessor processor(String id, GenerationStage stage) {
        return processor(id, stage, true, ProcessorResult.ok());
    }

    private GenerationStageProcessor processor(
            String id, GenerationStage stage, boolean supports, ProcessorResult result) {
        return new GenerationStageProcessor() {
            @Override public String id() { return id; }
            @Override public GenerationStage stage() { return stage; }
            @Override public boolean supports(GenerationContext context) { return supports; }
            @Override public ProcessorResult process(GenerationProcessingContext context) {
                calls.add(id);
                return result;
            }
        };
    }

    private static GenerationProcessingContext context() {
        GenerationContext context = new GenerationContext(
                "test", "com", "T", "T", "egovframework.let.t", "/tmp/out", "5.0", "jsp", Map.of());
        return GenerationProcessingContext.beforeRender(
                new GenerationBlueprint(context, List.of(), List.of(), List.of()));
    }
}
