package com.krdevops.springai.controller;

import com.krdevops.springai.model.contract.PipelineReleaseGateResponse;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import com.krdevops.springai.service.pipeline.McpRegisteredToolCatalog;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.service.controlplane.GenerationControlPlaneService;
import com.krdevops.springai.service.controlplane.ReadinessComparisonObserver;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final GenerationControlPlaneService controlPlane;
    private final ReadinessComparisonObserver comparisonObserver;
    public PipelineReleaseGateController(PipelineReleaseReadiness readiness, PipelineApiOperationCatalog catalog,
                                         McpRegisteredToolCatalog registeredTools) {
        this(readiness, catalog, registeredTools, null, null);
    }
    public PipelineReleaseGateController(PipelineReleaseReadiness readiness, PipelineApiOperationCatalog catalog,
                                         McpRegisteredToolCatalog registeredTools,
                                         GenerationControlPlaneService controlPlane) {
        this(readiness, catalog, registeredTools, controlPlane, null);
    }
    @Autowired
    public PipelineReleaseGateController(PipelineReleaseReadiness readiness, PipelineApiOperationCatalog catalog,
                                         McpRegisteredToolCatalog registeredTools,
                                         GenerationControlPlaneService controlPlane,
                                         ReadinessComparisonObserver comparisonObserver) {
        this.readiness = readiness;
        this.catalog = catalog;
        this.registeredTools = registeredTools;
        this.controlPlane = controlPlane;
        this.comparisonObserver = comparisonObserver;
    }
    @PostMapping("/release-readiness")
    public PipelineReleaseGateResponse evaluate(
            @RequestBody(required = false) Map<String, Boolean> gates,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String operationId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) GenerationSourceType sourceType) {
        if (operationId != null && !operationId.isBlank()) {
            if (controlPlane == null) {
                throw new IllegalStateException("GENERATION_CONTROL_PLANE_NOT_CONFIGURED");
            }
            ReleaseReadiness common = controlPlane.readiness(operationId, sourceType);
            if (gates != null && !gates.isEmpty() && comparisonObserver != null) {
                comparisonObserver.observe(operationId, sourceType, readiness.evaluate(gates), common);
            }
            return fromCommon(common);
        }
        return PipelineReleaseGateResponse.from(readiness.evaluate(gates));
    }
    public PipelineReleaseGateResponse evaluate(Map<String, Boolean> gates) {
        return evaluate(gates, null, null);
    }
    private PipelineReleaseGateResponse fromCommon(ReleaseReadiness common) {
        java.util.LinkedHashMap<String, Boolean> gates = new java.util.LinkedHashMap<>();
        java.util.Set<String> failed = new java.util.LinkedHashSet<>(common.failedGateNames());
        java.util.Set<String> missing = new java.util.LinkedHashSet<>(common.missingGateNames());
        for (String gate : java.util.List.of("BINDING", "BUILD", "RENDER")) {
            gates.put(gate, !failed.contains(gate) && !missing.contains(gate));
        }
        return new PipelineReleaseGateResponse(common.releaseReady(), gates,
                gates.entrySet().stream().filter(entry -> !entry.getValue()).map(Map.Entry::getKey).toList());
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
