package com.krdevops.springai.controller;

import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import com.krdevops.springai.service.pipeline.McpRegisteredToolCatalog;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineReleaseGateControllerTest {
    @Test void evaluate_returnsApiContract() {
        var response = new PipelineReleaseGateController(new PipelineReleaseReadiness(), new PipelineApiOperationCatalog(), new McpRegisteredToolCatalog(ToolCallbackProvider.from()))
                .evaluate(Map.of("binding", true, "build", false));
        assertThat(response.ready()).isFalse();
        assertThat(response.failedGateNames()).containsExactly("build");
    }

    @Test void operations_returnsCatalogContract() {
        var controller = new PipelineReleaseGateController(new PipelineReleaseReadiness(), new PipelineApiOperationCatalog(), new McpRegisteredToolCatalog(ToolCallbackProvider.from()));
        assertThat(controller.operations()).hasSize(7);
    }
}
