package com.krdevops.springai.controller;

import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.service.controlplane.GenerationControlPlaneService;
import com.krdevops.springai.service.controlplane.ReadinessComparisonObserver;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.pipeline.McpRegisteredToolCatalog;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PipelineReleaseGateControlPlaneCompatibilityTest {

    @Test
    void operationId가_있으면_기존_응답_형식을_유지하며_공통_Readiness를_사용한다() {
        GenerationControlPlaneService controlPlane = mock(GenerationControlPlaneService.class);
        given(controlPlane.readiness("op", GenerationSourceType.CRUD)).willReturn(new ReleaseReadiness(
                "op", GenerationSourceType.CRUD, false, EvidenceRecordingStatus.RECORDED,
                List.of("BINDING"), List.of("BUILD"), "hash"));
        var controller = new PipelineReleaseGateController(new PipelineReleaseReadiness(),
                new PipelineApiOperationCatalog(), new McpRegisteredToolCatalog(ToolCallbackProvider.from()),
                controlPlane);

        var response = controller.evaluate(null, "op", GenerationSourceType.CRUD);

        assertThat(response.ready()).isFalse();
        assertThat(response.gates()).containsEntry("BINDING", false)
                .containsEntry("BUILD", false).containsEntry("RENDER", true);
        assertThat(response.failedGateNames()).containsExactly("BINDING", "BUILD");
    }

    @Test
    void 기존_Gate와_operationId가_함께_오면_두_판정을_비교_관측한다() {
        GenerationControlPlaneService controlPlane = mock(GenerationControlPlaneService.class);
        ReadinessComparisonObserver observer = mock(ReadinessComparisonObserver.class);
        ReleaseReadiness common = new ReleaseReadiness("op", GenerationSourceType.CRUD, true,
                EvidenceRecordingStatus.RECORDED, List.of(), List.of(), "hash");
        given(controlPlane.readiness("op", GenerationSourceType.CRUD)).willReturn(common);
        var controller = new PipelineReleaseGateController(new PipelineReleaseReadiness(),
                new PipelineApiOperationCatalog(), new McpRegisteredToolCatalog(ToolCallbackProvider.from()),
                controlPlane, observer);
        controller.evaluate(java.util.Map.of("BINDING", true, "BUILD", true, "RENDER", true),
                "op", GenerationSourceType.CRUD);
        verify(observer).observe(org.mockito.ArgumentMatchers.eq("op"),
                org.mockito.ArgumentMatchers.eq(GenerationSourceType.CRUD),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(common));
    }
}
