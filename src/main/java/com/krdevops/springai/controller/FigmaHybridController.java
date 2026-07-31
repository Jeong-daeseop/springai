package com.krdevops.springai.controller;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridApprovalRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidateRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridExportResult;
import com.krdevops.springai.model.figma.hybrid.HybridConversionReport;
import com.krdevops.springai.service.figma.FigmaHybridExportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** R7: Reference Snapshot → 후보 Preview·수정·승인 → Semantic 화면 API. */
@RestController
@RequestMapping("/api/figma/hybrid")
@RequiredArgsConstructor
public class FigmaHybridController {

    private final FigmaHybridExportService hybridExportService;

    @PostMapping("/candidates")
    public FigmaHybridCandidate createCandidate(
            @RequestBody FigmaHybridCandidateRequest request
    ) {
        try {
            return hybridExportService.createCandidate(request);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("FIGMA_HYBRID_CANDIDATE_INVALID", exception.getMessage());
        }
    }

    @GetMapping("/{artifactId}/candidate")
    public FigmaHybridCandidate getCandidate(@PathVariable String artifactId) {
        try {
            return hybridExportService.getCandidate(artifactId);
        } catch (IllegalArgumentException exception) {
            throw new FigmaResourceNotFoundException(
                    "FIGMA_HYBRID_CANDIDATE_NOT_FOUND", exception.getMessage());
        }
    }

    @PutMapping("/{artifactId}/candidate")
    public FigmaHybridCandidate reviseCandidate(
            @PathVariable String artifactId,
            @RequestBody ScreenSpecification revised
    ) {
        try {
            return hybridExportService.reviseCandidate(artifactId, revised);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("FIGMA_HYBRID_REVISION_INVALID", exception.getMessage());
        }
    }

    @PostMapping("/{artifactId}/approve")
    public FigmaHybridExportResult approve(
            @PathVariable String artifactId,
            @RequestBody FigmaHybridApprovalRequest request
    ) {
        try {
            return hybridExportService.approveAndExport(artifactId, request);
        } catch (IllegalArgumentException exception) {
            throw new FigmaRequestException("FIGMA_HYBRID_APPROVAL_INVALID", exception.getMessage());
        }
    }

    @GetMapping("/{artifactId}/result")
    public FigmaHybridExportResult getResult(@PathVariable String artifactId) {
        try {
            return hybridExportService.getResult(artifactId);
        } catch (IllegalArgumentException exception) {
            throw new FigmaResourceNotFoundException(
                    "FIGMA_HYBRID_RESULT_NOT_FOUND", exception.getMessage());
        }
    }

    @GetMapping("/{artifactId}/report")
    public HybridConversionReport getReport(@PathVariable String artifactId) {
        try {
            return hybridExportService.getResult(artifactId).report();
        } catch (IllegalArgumentException noResult) {
            try {
                return hybridExportService.getCandidate(artifactId).report();
            } catch (IllegalArgumentException noCandidate) {
                throw new FigmaResourceNotFoundException(
                        "FIGMA_HYBRID_REPORT_NOT_FOUND", noCandidate.getMessage());
            }
        }
    }
}
