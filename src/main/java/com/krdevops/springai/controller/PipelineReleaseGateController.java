package com.krdevops.springai.controller;

import com.krdevops.springai.model.contract.PipelineReleaseGateResponse;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import com.krdevops.springai.service.pipeline.McpRegisteredToolCatalog;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/** Release Gate 평가 결과를 사람·Agent가 동일한 API 계약으로 조회한다. */
@RestController
@RequestMapping("/api/pipeline")
public class PipelineReleaseGateController {
    private final PipelineReleaseReadiness readiness;
    private final PipelineApiOperationCatalog catalog;
    private final McpRegisteredToolCatalog registeredTools;
    public PipelineReleaseGateController(PipelineReleaseReadiness readiness, PipelineApiOperationCatalog catalog, McpRegisteredToolCatalog registeredTools) { this.readiness = readiness; this.catalog = catalog; this.registeredTools = registeredTools; }
    @PostMapping("/release-readiness")
    public PipelineReleaseGateResponse evaluate(@RequestBody Map<String, Boolean> gates) {
        return PipelineReleaseGateResponse.from(readiness.evaluate(gates));
    }
    @org.springframework.web.bind.annotation.GetMapping("/operations")
    public java.util.List<PipelineApiOperationCatalog.Operation> operations() { return catalog.operations(); }
    @org.springframework.web.bind.annotation.GetMapping("/mcp-tools")
    public McpToolResponse mcpTools(@org.springframework.web.bind.annotation.RequestParam(required = false) String expectedHash) {
        String actualHash = registeredTools.snapshotHash();
        String baseline = expectedHash != null ? expectedHash.trim() : System.getenv("MCP_TOOL_SNAPSHOT_HASH");
        if (baseline != null) baseline = baseline.trim();
        return new McpToolResponse(registeredTools.toolNames(), actualHash,
                baseline == null ? null : registeredTools.matchesSnapshot(baseline));
    }
    public record McpToolResponse(java.util.List<String> names, String snapshotHash, Boolean baselineMatched) {}
}
