package com.krdevops.springai.controller;

import com.krdevops.springai.model.figma.ops.DesignSystemImpact;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import com.krdevops.springai.model.figma.ops.FigmaOperationalMetrics;
import com.krdevops.springai.service.figma.FigmaOperationsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** R8 운영 보고서·지표·Design System 영향 조회 API. */
@RestController
@RequestMapping("/api/figma/operations")
@RequiredArgsConstructor
public class FigmaOperationsController {

    private final FigmaOperationsService operationsService;

    @PostMapping("/reports")
    public FigmaGenerationReport record(@jakarta.validation.Valid @RequestBody FigmaGenerationReport report) {
        try {
            return operationsService.record(report);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new FigmaRequestException("FIGMA_GENERATION_REPORT_INVALID", exception.getMessage());
        }
    }

    @GetMapping("/screens/{screenId}/reports")
    public List<FigmaGenerationReport> reports(@PathVariable String screenId) {
        return operationsService.reports(screenId);
    }

    @GetMapping("/metrics")
    public FigmaOperationalMetrics metrics() {
        return operationsService.metrics();
    }

    @GetMapping("/design-system-impact/{profileId}")
    public DesignSystemImpact impact(
            @PathVariable String profileId,
            @RequestParam(required = false) String profileVersion,
            @RequestParam(required = false) String registryVersion
    ) {
        return operationsService.impact(profileId, profileVersion, registryVersion);
    }
}
