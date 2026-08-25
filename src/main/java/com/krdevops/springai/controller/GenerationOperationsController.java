package com.krdevops.springai.controller;

import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.model.controlplane.GenerationOperationsMetrics;
import com.krdevops.springai.model.controlplane.ReadinessComparisonReport;
import com.krdevops.springai.service.controlplane.GenerationControlPlaneService;
import com.krdevops.springai.service.controlplane.ReadinessComparisonObserver;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 기존 Apply API와 분리된 공통 읽기 전용 Operation API. */
@RestController
@RequestMapping("/api/generation-operations")
public class GenerationOperationsController {

    private final GenerationControlPlaneService service;
    private final ReadinessComparisonObserver comparisonObserver;

    public GenerationOperationsController(GenerationControlPlaneService service,
                                          ReadinessComparisonObserver comparisonObserver) {
        this.service = service;
        this.comparisonObserver = comparisonObserver;
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<GenerationOperation> operation(
            @PathVariable String operationId,
            @RequestParam(required = false) GenerationSourceType sourceType) {
        return service.find(operationId, sourceType).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/metrics")
    public GenerationOperationsMetrics metrics() {
        return service.metrics();
    }

    @GetMapping("/metrics/readiness-comparison")
    public ReadinessComparisonObserver.ComparisonMetrics readinessComparisonMetrics() {
        return comparisonObserver.metrics();
    }

    @GetMapping("/metrics/readiness-comparison/report")
    public ReadinessComparisonReport readinessComparisonReport(
            @RequestParam(required = false) java.time.Instant from,
            @RequestParam(required = false) java.time.Instant to) {
        java.time.Instant effectiveTo = to == null ? java.time.Instant.now() : to;
        java.time.Instant effectiveFrom = from == null ? effectiveTo.minus(java.time.Duration.ofDays(30)) : from;
        return comparisonObserver.report(effectiveFrom, effectiveTo);
    }

    @GetMapping("/{operationId}/evidence")
    public List<ValidationEvidence> evidence(
            @PathVariable String operationId,
            @RequestParam(required = false) GenerationSourceType sourceType) {
        return service.evidence(operationId, sourceType);
    }

    @GetMapping("/{operationId}/readiness")
    public ReleaseReadiness readiness(
            @PathVariable String operationId,
            @RequestParam(required = false) GenerationSourceType sourceType) {
        return service.readiness(operationId, sourceType);
    }
}
