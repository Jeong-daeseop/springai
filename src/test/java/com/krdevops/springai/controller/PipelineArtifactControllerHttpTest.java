package com.krdevops.springai.controller;

import com.krdevops.springai.service.evidence.PreviewEvidenceBundleRepository;
import com.krdevops.springai.service.handoff.ScreenHandoffBundleRepository;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class PipelineArtifactControllerHttpTest {
    private final ScreenHandoffBundleRepository handoff = new ScreenHandoffBundleRepository();
    private final MockMvc mvc = standaloneSetup(new PipelineArtifactController(
            new PreviewEvidenceBundleRepository(), handoff)).build();
    @Test void missingEvidence_returns404() throws Exception {
        mvc.perform(get("/api/pipeline/evidence/missing")).andExpect(status().isNotFound());
    }
    @Test void missingHandoff_returns404() throws Exception {
        mvc.perform(get("/api/pipeline/handoff/missing")).andExpect(status().isNotFound());
    }
    @Test void missingHandoffProjection_returns404() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/pipeline/handoff/missing/projection")
                        .param("audience", "AGENT").contentType("application/json").content("{\"build\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test void handoffProjection_includesReleaseGateAndAuditHash() throws Exception {
        String auditHash = "0".repeat(64);
        String id = "handoff-http";
        String canonical = id + "|op|rev|[]|[]|[]|[]|[]|" + auditHash;
        String contentHash = ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
        handoff.save(new ScreenHandoffBundle(id, contentHash, "op", "rev", List.of(), List.of(),
                List.of(), List.of(), List.of(), auditHash));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                        "/api/pipeline/handoff/" + id + "/projection")
                        .param("audience", "AGENT")
                        .contentType("application/json")
                        .content("{\"build\":false}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.releaseReady").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.failedGateNames[0]").value("build"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.auditSnapshotHash").value(auditHash));
    }

}
