package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.generation.pipeline.GenerationHistoryRecorder;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import com.krdevops.springai.service.generation.pipeline.HistoryRecordResult;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterDetailGenerationPipelineServiceTest {

    @Mock MasterDetailGenerationPlanner planner;
    @Mock MasterDetailGenerationRenderer renderer;
    @Mock GenerationExecutor executor;
    @Mock GenerationProcessorRunner processorRunner;
    @Mock GenerationVerifierRunner verifierRunner;
    @Mock GenerationHistoryRecorder historyRecorder;

    @Test
    void failedPlan_doesNotRenderOrWrite() {
        MasterDetailGenerationCommand command = new MasterDetailGenerationCommand(
                "com", "MASTER", "DETAIL", "Order", "egovframework.let.order", Path.of("/tmp/out"),
                "auto", "5.0", "jsp", null, null);
        MasterDetailGenerationPlan failed = MasterDetailGenerationPlan.rejected(
                new MasterDetailPlanFailure(MasterDetailPlanFailure.Kind.TABLE_NOT_FOUND,
                        "테이블 없음", List.of("MASTER", "DETAIL")));
        when(planner.plan(command)).thenReturn(failed);

        var service = new MasterDetailGenerationPipelineService(
                planner, renderer, executor, processorRunner, verifierRunner, historyRecorder);
        MasterDetailPipelineResult result = service.execute(command);

        assertThat(result.planned()).isFalse();
        assertThat(result.execution()).isNull();
        verifyNoInteractions(renderer, executor, processorRunner, verifierRunner, historyRecorder);
    }

    @Test
    void successfulPlan_executesWriteThenPostWriteRunner() {
        MasterDetailGenerationCommand command = new MasterDetailGenerationCommand(
                "com", "MASTER", "DETAIL", "Order", "egovframework.let.order", Path.of("/tmp/out"),
                "auto", "5.0", "jsp", null, null);
        MasterDetailGenerationPlan plan = mock(MasterDetailGenerationPlan.class);
        RenderedGenerationPlan rendered = new RenderedGenerationPlan(
                new GenerationContext("master-detail", "com", "MASTER", "Order",
                        "egovframework.let.order", "/tmp/out", "5.0", "jsp", java.util.Map.of()),
                List.of(), List.of(), List.of());
        GenerationExecution execution = new GenerationExecution(rendered, List.of(), List.of());
        when(plan.failed()).thenReturn(false);
        when(planner.plan(command)).thenReturn(plan);
        when(renderer.render(plan, command)).thenReturn(rendered);
        when(executor.execute(rendered)).thenReturn(execution);
        when(processorRunner.run(any(), eq(List.of()), any()))
                .thenReturn(new GenerationProcessorRunner.ProcessorRunResult(false, null, List.of()));
        when(verifierRunner.run(any()))
                .thenReturn(new GenerationVerifierRunner.VerificationRunResult("OK", List.of()));
        when(historyRecorder.record(any())).thenReturn(new HistoryRecordResult("history"));

        var service = new MasterDetailGenerationPipelineService(
                planner, renderer, executor, processorRunner, verifierRunner, historyRecorder);
        var result = service.execute(command);

        assertThat(result.planned()).isTrue();
        assertThat(result.execution()).isSameAs(execution);
        verify(executor).execute(rendered);
        verify(processorRunner).run(any(), eq(List.of()), any());
    }

    @Test
    void postWriteProcessorFailures_areReturnedInPipelineResult() {
        MasterDetailGenerationCommand command = new MasterDetailGenerationCommand(
                "com", "MASTER", "DETAIL", "Order", "egovframework.let.order", Path.of("/tmp/out"),
                "auto", "5.0", "jsp", null, null);
        MasterDetailGenerationPlan plan = mock(MasterDetailGenerationPlan.class);
        RenderedGenerationPlan rendered = new RenderedGenerationPlan(
                new GenerationContext("master-detail", "com", "MASTER", "Order",
                        "egovframework.let.order", "/tmp/out", "5.0", "jsp", Map.of()),
                List.of(), List.of(), List.of());
        GenerationExecution execution = new GenerationExecution(rendered, List.of(), List.of());
        when(plan.failed()).thenReturn(false);
        when(planner.plan(command)).thenReturn(plan);
        when(renderer.render(plan, command)).thenReturn(rendered);
        when(executor.execute(rendered)).thenReturn(execution);
        when(processorRunner.run(any(), eq(List.of()), any())).thenReturn(
                new GenerationProcessorRunner.ProcessorRunResult(false, null,
                        List.of(new GenerationFailure("processor", "보강 실패"))));
        when(verifierRunner.run(any())).thenReturn(
                new GenerationVerifierRunner.VerificationRunResult("OK", List.of()));
        when(historyRecorder.record(any())).thenReturn(new HistoryRecordResult("history"));

        var result = new MasterDetailGenerationPipelineService(
                planner, renderer, executor, processorRunner, verifierRunner, historyRecorder).execute(command);

        assertThat(result.failures()).extracting(GenerationFailure::description).containsExactly("보강 실패");
    }
}
