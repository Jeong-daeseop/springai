package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;
import com.krdevops.springai.service.generation.pipeline.GenerationExecutor;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessorRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import com.krdevops.springai.service.generation.pipeline.GenerationHistoryRecorder;
import com.krdevops.springai.service.generation.pipeline.HistoryRecordResult;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BoardGenerationPipelineServiceTest {

    @Mock BoardGenerationPlanner planner;
    @Mock BoardGenerationRenderer renderer;
    @Mock GenerationExecutor executor;
    @Mock GenerationProcessorRunner processorRunner;
    @Mock GenerationVerifierRunner verifierRunner;
    @Mock GenerationHistoryRecorder historyRecorder;

    @Test
    void failedPlan_doesNotRenderOrWrite() {
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Bbs", "egovframework.let.bbs", Path.of("/tmp/out"),
                null, null, null, null, null, "5.0", "jsp", null, null, null, null);
        BoardGenerationPlan failed = BoardGenerationPlan.rejected(new BoardPlanFailure(
                BoardPlanFailure.Kind.TABLE_NOT_FOUND, "테이블 없음", java.util.List.of("missing")));
        when(planner.plan(command)).thenReturn(failed);

        BoardPipelineResult result = new BoardGenerationPipelineService(planner, renderer, executor).execute(command);

        assertThat(result.planned()).isFalse();
        assertThat(result.execution()).isNull();
        verifyNoInteractions(renderer, executor);
    }

    @Test
    void successfulPlan_rendersThenExecutes() {
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Bbs", "egovframework.let.bbs", Path.of("/tmp/out"),
                null, null, null, null, null, "5.0", "jsp", null, null, null, null);
        BoardGenerationPlan plan = mock(BoardGenerationPlan.class);
        RenderedGenerationPlan rendered = mock(RenderedGenerationPlan.class);
        GenerationExecution execution = mock(GenerationExecution.class);
        when(plan.failed()).thenReturn(false);
        when(planner.plan(command)).thenReturn(plan);
        when(renderer.render(plan, command)).thenReturn(rendered);
        when(executor.execute(rendered)).thenReturn(execution);

        BoardPipelineResult result = new BoardGenerationPipelineService(planner, renderer, executor).execute(command);

        assertThat(result.planned()).isTrue();
        assertThat(result.execution()).isSameAs(execution);
        verify(renderer).render(plan, command);
        verify(executor).execute(rendered);
    }

    @Test
    void successfulPipeline_runsPostWriteVerifierAndHistory() {
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Bbs", "egovframework.let.bbs", Path.of("/tmp/out"),
                null, null, null, null, null, "5.0", "jsp", null, null, null, null);
        BoardGenerationPlan plan = mock(BoardGenerationPlan.class);
        RenderedGenerationPlan rendered = new RenderedGenerationPlan(
                new GenerationContext("board", "com", "BBS", "Bbs", "egovframework.let.bbs",
                        "/tmp/out", "5.0", "jsp", Map.of()), List.of(), List.of(), List.of());
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

        var service = new BoardGenerationPipelineService(
                planner, renderer, executor, processorRunner, verifierRunner, historyRecorder);
        var result = service.execute(command);

        assertThat(result.execution()).isSameAs(execution);
        verify(executor).execute(rendered);
        verify(processorRunner).run(any(), eq(List.of()), any());
        verify(verifierRunner).run(any());
        verify(historyRecorder).record(any());
    }

    @Test
    void postWriteProcessorFailures_areReturnedInPipelineResult() {
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Bbs", "egovframework.let.bbs", Path.of("/tmp/out"),
                null, null, null, null, null, "5.0", "jsp", null, null, null, null);
        BoardGenerationPlan plan = mock(BoardGenerationPlan.class);
        RenderedGenerationPlan rendered = new RenderedGenerationPlan(
                new GenerationContext("board", "com", "BBS", "Bbs", "egovframework.let.bbs",
                        "/tmp/out", "5.0", "jsp", Map.of()), List.of(), List.of(), List.of());
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

        var result = new BoardGenerationPipelineService(
                planner, renderer, executor, processorRunner, verifierRunner, historyRecorder).execute(command);

        assertThat(result.processorFailures()).extracting(GenerationFailure::description)
                .containsExactly("보강 실패");
    }
}
