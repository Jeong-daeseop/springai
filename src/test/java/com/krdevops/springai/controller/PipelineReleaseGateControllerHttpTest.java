package com.krdevops.springai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import com.krdevops.springai.service.pipeline.McpRegisteredToolCatalog;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PipelineReleaseGateControllerHttpTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockMvc mvc = standaloneSetup(new PipelineReleaseGateController(
            new PipelineReleaseReadiness(), new PipelineApiOperationCatalog(), new McpRegisteredToolCatalog(ToolCallbackProvider.from()))).build();

    @Test void releaseReadiness_isExposedAsJsonContract() throws Exception {
        mvc.perform(post("/api/pipeline/release-readiness")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(java.util.Map.of("binding", true, "build", false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.failedGateNames[0]").value("build"));
    }

    @Test void operations_areExposedAsJsonCatalog() throws Exception {
        mvc.perform(get("/api/pipeline/operations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists())
                .andExpect(jsonPath("$[0].risk").exists());
    }

    @Test void mcpTools_exposesRuntimeSnapshot() throws Exception {
        mvc.perform(get("/api/pipeline/mcp-tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray())
                .andExpect(jsonPath("$.snapshotHash").isString());
    }

    @Test void mcpTools_canCompareExpectedBaselineHash() throws Exception {
        String hash = new McpRegisteredToolCatalog(ToolCallbackProvider.from()).snapshotHash();
        mvc.perform(get("/api/pipeline/mcp-tools").param("expectedHash", hash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineMatched").value(true));
    }

    @Test void mcpTools_trimsExpectedBaselineHash() throws Exception {
        String hash = new McpRegisteredToolCatalog(ToolCallbackProvider.from()).snapshotHash();
        mvc.perform(get("/api/pipeline/mcp-tools").param("expectedHash", "  " + hash + "  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineMatched").value(true));
    }

    @Test void mcpTools_reportsBaselineMismatch() throws Exception {
        mvc.perform(get("/api/pipeline/mcp-tools").param("expectedHash", "0".repeat(64)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineMatched").value(false));
    }

    @Test void mcpTools_rejectsMalformedExpectedHashAsMismatch() throws Exception {
        mvc.perform(get("/api/pipeline/mcp-tools").param("expectedHash", "not-a-hash"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baselineMatched").value(false));
    }
}
