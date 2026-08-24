package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.InMemoryCrudGenerationSnapshotStore;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlan;
import com.krdevops.springai.service.generation.crud.CrudGenerationPlanner;
import com.krdevops.springai.service.generation.model.FileBlueprint;
import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.GenerationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

class CrudGenerationSnapshotToolTest {

    @TempDir
    Path outputRoot;

    @Test
    void 디스크에_있는_현재_내용을_그대로_스냅샷으로_등록하고_파일은_건드리지_않는다() throws Exception {
        Path voFile = outputRoot.resolve("EmployerVO.java");
        Files.writeString(voFile, "class EmployerVO { /* hand edited long ago */ }");

        CrudGenerationPlanner planner = Mockito.mock(CrudGenerationPlanner.class);
        GenerationBlueprint blueprint = new GenerationBlueprint(
                new GenerationContext("crud", "ebt", "EMP", "emp", "egovframework.let.emp",
                        outputRoot.toString(), "5.0", "thymeleaf", Map.of()),
                List.of(new FileBlueprint("vo", "EmployerVO.java", voFile, null)),
                List.of(), List.of());
        given(planner.plan(org.mockito.ArgumentMatchers.any())).willReturn(
                new CrudGenerationPlan(blueprint, null, CrudProgramMetadata.fallback(null), null, List.of()));

        InMemoryCrudGenerationSnapshotStore snapshotStore = new InMemoryCrudGenerationSnapshotStore();
        CrudGenerationSnapshotTool tool = new CrudGenerationSnapshotTool(planner, snapshotStore);

        String result = tool.adoptCurrentAsBaseline(
                "ebt", "EMP", "emp", "egovframework.let.emp", outputRoot.toString(), "thymeleaf");

        assertThat(result).contains("채택");
        assertThat(voFile).hasContent("class EmployerVO { /* hand edited long ago */ }"); // 파일 미변경
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                outputRoot.toString(), "EMP", "thymeleaf");
        GenerationOwnershipManifest adopted = snapshotStore.findLatest(operationId).orElseThrow();
        assertThat(adopted.artifacts()).hasSize(1);
        assertThat(adopted.artifacts().get(0).artifactPath()).isEqualTo("EmployerVO.java");
    }
}
