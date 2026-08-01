package com.krdevops.springai.service.generation.masterdetail;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedFilePlan;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;

import static org.assertj.core.api.Assertions.assertThat;

class MasterDetailGenerationResultAssemblerTest {

    @Test
    void tableNotFoundPlan_mapsToLegacyNotFoundResult() {
        MasterDetailGenerationCommand command = new MasterDetailGenerationCommand(
                "com", "MASTER", "DETAIL", "Order", "egovframework.let.order", Path.of("/tmp/out"),
                "auto", "5.0", "jsp", null, null);
        MasterDetailGenerationPlan plan = MasterDetailGenerationPlan.rejected(new MasterDetailPlanFailure(
                MasterDetailPlanFailure.Kind.TABLE_NOT_FOUND, "테이블 없음", List.of("MASTER", "DETAIL")));

        var result = new MasterDetailGenerationResultAssembler().assemble(command,
                new MasterDetailPipelineResult(plan, null));

        assertThat(result.tableNotFound()).isTrue();
        assertThat(result.database()).isEqualTo("com");
        assertThat(result.failedFiles()).containsExactly("MASTER", "DETAIL");
        assertThat(result.succeededFiles()).isEmpty();
    }

    @Test
    void successfulExecution_preservesSucceededFilesAndSummaries() {
        MasterDetailGenerationCommand command = new MasterDetailGenerationCommand(
                "com", "MASTER", "DETAIL", "Order", "egovframework.let.order", Path.of("/tmp/out"),
                "auto", "5.0", "jsp", null, null);
        MasterDetailGenerationPlan plan = org.mockito.Mockito.mock(MasterDetailGenerationPlan.class);
        org.mockito.Mockito.when(plan.failed()).thenReturn(false);
        RenderedFilePlan file = RenderedFilePlan.rendered(
                new com.krdevops.springai.service.generation.model.FileBlueprint(
                        "list", "EgovOrderList.jsp", Path.of("/tmp/out/EgovOrderList.jsp"), null), "source");
        GenerationExecution execution = new GenerationExecution(
                new RenderedGenerationPlan(null, List.of(file), List.of(), List.of()), List.of(file), List.of());
        var result = new MasterDetailGenerationResultAssembler().assemble(command,
                new MasterDetailPipelineResult(plan, execution, List.of(), "검증 OK", "이력 OK"));

        assertThat(result.succeededFiles()).containsExactly("EgovOrderList.jsp");
        assertThat(result.validationSummary()).isEqualTo("검증 OK");
        assertThat(result.historySummary()).isEqualTo("이력 OK");
    }
}
