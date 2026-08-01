package com.krdevops.springai.service.generation.board;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BoardGenerationResultAssemblerTest {

    @Test
    void tableNotFoundPlan_mapsToLegacyNotFoundResult() {
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Notice", "egovframework.let.notice", Path.of("/tmp/out"),
                "LETTNBBS", null, null, null, null, "5.0", "jsp", null, null, null, null);
        BoardGenerationPlan plan = BoardGenerationPlan.rejected(new BoardPlanFailure(
                BoardPlanFailure.Kind.TABLE_NOT_FOUND, "게시판 테이블 없음", List.of("LETTNBBS")));

        var result = new BoardGenerationResultAssembler().assemble(command, plan,
                new BoardPipelineResult(plan, null), "", "");

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.database()).isEqualTo("com");
        assertThat(result.failedFiles()).containsExactly("LETTNBBS");
        assertThat(result.succeededFiles()).isEmpty();
    }

    @Test
    void successfulExecution_preservesSucceededFilesAndSummaries() {
        BoardGenerationCommand command = new BoardGenerationCommand(
                "com", "Notice", "egovframework.let.notice", Path.of("/tmp/out"),
                "LETTNBBS", null, null, null, null, "5.0", "jsp", null, null, null, null);
        BoardGenerationPlan plan = org.mockito.Mockito.mock(BoardGenerationPlan.class);
        org.mockito.Mockito.when(plan.failed()).thenReturn(false);
        var metadata = org.mockito.Mockito.mock(com.krdevops.springai.model.board.BoardProgramMetadata.class);
        var model = org.mockito.Mockito.mock(com.krdevops.springai.model.board.BoardTemplateModel.class);
        var route = org.mockito.Mockito.mock(com.krdevops.springai.model.board.BoardRouteModel.class);
        when(plan.metadata()).thenReturn(metadata);
        when(plan.model()).thenReturn(model);
        when(model.route()).thenReturn(route);
        when(metadata.menuIntegrationStatus()).thenReturn("OK");
        when(metadata.programKoreanName()).thenReturn("공지");
        when(metadata.registeredUrl()).thenReturn("/notice/list.do");
        when(metadata.defaultBbsId()).thenReturn(null);
        when(route.registeredListPath()).thenReturn("/notice/List.do");
        RenderedFilePlan file = RenderedFilePlan.rendered(
                new com.krdevops.springai.service.generation.model.FileBlueprint(
                        "list", "EgovNoticeList.jsp", Path.of("/tmp/out/EgovNoticeList.jsp"), null), "source");
        GenerationExecution execution = new GenerationExecution(
                new RenderedGenerationPlan(null, List.of(file), List.of(), List.of()), List.of(file), List.of());
        var result = new BoardGenerationResultAssembler().assemble(command, plan,
                new BoardPipelineResult(plan, execution, List.of(), "검증 OK", "이력 OK"), "검증 OK", "이력 OK");

        assertThat(result.succeededFiles()).containsExactly("EgovNoticeList.jsp");
        assertThat(result.validationSummary()).isEqualTo("검증 OK");
        assertThat(result.historySummary()).isEqualTo("이력 OK");
    }
}
