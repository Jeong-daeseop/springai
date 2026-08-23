package com.krdevops.springai.controller;

import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import com.krdevops.springai.service.evidence.PreviewEvidenceBundleRepository;
import com.krdevops.springai.service.handoff.ScreenHandoffBundleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.handoff.ScreenHandoffProjectionService;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineArtifactController {
    private final PreviewEvidenceBundleRepository evidence;
    private final ScreenHandoffBundleRepository handoff;
    private final PipelineReleaseReadiness releaseReadiness;
    private final ScreenHandoffProjectionService projection;
    @org.springframework.beans.factory.annotation.Autowired
    public PipelineArtifactController(PreviewEvidenceBundleRepository evidence, ScreenHandoffBundleRepository handoff,
                                      PipelineReleaseReadiness releaseReadiness, ScreenHandoffProjectionService projection) { this.evidence = evidence; this.handoff = handoff; this.releaseReadiness = releaseReadiness; this.projection = projection; }
    public PipelineArtifactController(PreviewEvidenceBundleRepository evidence, ScreenHandoffBundleRepository handoff) {
        this(evidence, handoff, new PipelineReleaseReadiness(), new ScreenHandoffProjectionService());
    }
    @GetMapping("/evidence/{bundleId}")
    public PreviewEvidenceBundle evidence(@PathVariable String bundleId) { return evidence.find(bundleId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence Bundle을 찾을 수 없습니다.")); }
    @GetMapping("/handoff/{bundleId}")
    public ScreenHandoffBundle handoff(@PathVariable String bundleId) { return handoff.find(bundleId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Handoff Bundle을 찾을 수 없습니다.")); }
    @PostMapping("/handoff/{bundleId}/projection")
    public ScreenHandoffProjectionService.Projection handoffProjection(@PathVariable String bundleId,
                                                                         @RequestParam ScreenHandoffProjectionService.Audience audience,
                                                                         @RequestBody java.util.Map<String, Boolean> gates) {
        return projection.projectWithReleaseGate(handoff(bundleId), audience,
                com.krdevops.springai.model.contract.PipelineReleaseGateResponse.from(releaseReadiness.evaluate(gates)));
    }
}
