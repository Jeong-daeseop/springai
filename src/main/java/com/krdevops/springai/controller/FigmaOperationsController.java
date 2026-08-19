package com.krdevops.springai.controller;

import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.ops.DesignSystemImpact;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import com.krdevops.springai.model.figma.ops.FigmaOperationalMetrics;
import com.krdevops.springai.service.FigmaApiException;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequestType;
import com.krdevops.springai.service.figma.FigmaDesignOperationService;
import com.krdevops.springai.service.figma.FigmaOperationsService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** R8 운영 보고서·지표·Design System 영향 조회 API + R5-041 Plugin Apply 보고서 수신. */
@RestController
@RequestMapping("/api/figma/operations")
@RequiredArgsConstructor
public class FigmaOperationsController {

    private final FigmaOperationsService operationsService;
    private final FigmaDesignOperationService designOperationService;
    private final DesignArtifactService artifactService;

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

    /**
     * R5-041/R6-047: Plugin이 Apply를 시작하기 직전 호출. PREVIEW_READY → APPLY_REQUIRED.
     * 이 호출 없이 곧바로 {@code /applied-report}를 보내면 상태가 여전히 PREVIEW_READY라서 거부된다.
     *
     * POST /api/figma/operations/{operationId}/apply-requested
     */
    @PostMapping("/{operationId}/apply-requested")
    public ResponseEntity<FigmaDesignOperation> requestApply(@PathVariable String operationId) {
        try {
            return ResponseEntity.ok(designOperationService.requestApply(operationId));
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new FigmaRequestException("FIGMA_OPERATION_APPLY_REQUEST_FAILED", e.getMessage());
        }
    }

    /**
     * R5-041: Plugin으로부터 화면 적용 완료 보고서 수신.
     * MCP 분석 완료(PREVIEW_READY)와 Plugin Apply 완료(APPLIED) 상태를 분리.
     *
     * 요청:
     * POST /api/figma/operations/{operationId}/applied-report
     * {
     *   "screenId": "user-list",
     *   "affectedNodeIds": ["node-1", "node-2"],
     *   "reuseCount": 3,
     *   "createdCount": 5,
     *   "fallbackCount": 0,
     *   "summary": "User list screen applied successfully"
     * }
     */
    @PostMapping("/{operationId}/applied-report")
    public ResponseEntity<FigmaDesignOperation> reportApplied(
            @PathVariable String operationId,
            @RequestBody PluginApplyReport report
    ) {
        try {
            FigmaDesignOperation updated = designOperationService.reportApplied(
                    operationId, report
            );
            return ResponseEntity.ok(updated);
        } catch (FigmaApiException e) {
            // scope 재검증 중 전파된 Figma 오류(인증 실패·rate limit 등)가 불투명한 500으로
            // 새지 않도록 원인 코드를 붙여 표준 응답으로 변환한다.
            throw new FigmaRequestException(
                    "FIGMA_OPERATION_APPLY_FAILED", e.code() + ": " + e.getMessage());
        } catch (IllegalStateException | IllegalArgumentException e) {
            throw new FigmaRequestException("FIGMA_OPERATION_APPLY_FAILED", e.getMessage());
        }
    }

    /**
     * R5-040: Operation의 operationId, request type, source revision 조회.
     * Plugin이 Preview 시 Operation 정보를 표시할 때 사용.
     *
     * GET /api/figma/operations/{operationId}/info
     */
    @GetMapping("/{operationId}/info")
    public ResponseEntity<FigmaDesignOperation> getOperation(@PathVariable String operationId) {
        return designOperationService.findOperation(operationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** R5-043: 멀티 스크린 Operation의 Bundle을 한 번에 내려받기 위한 원자 조회 API. */
    @GetMapping("/{operationId}/bundles")
    public List<OperationBundle> bundles(@PathVariable String operationId) {
        FigmaDesignOperation operation = designOperationService.findOperation(operationId)
                .orElseThrow(() -> new FigmaRequestException("FIGMA_OPERATION_NOT_FOUND", operationId));
        if (operation.request() == null || operation.request().type() != FigmaDesignRequestType.MULTI_SCREEN_FLOW) {
            throw new FigmaRequestException("FIGMA_MULTI_SCREEN_OPERATION_REQUIRED",
                    "MULTI_SCREEN_FLOW Operation만 Bundle 일괄 조회를 지원합니다.");
        }
        if (operation.artifacts() == null || operation.artifacts().isEmpty()) {
            throw new FigmaRequestException("FIGMA_OPERATION_BUNDLES_EMPTY", "조회할 Bundle이 없습니다.");
        }
        return operation.artifacts().stream()
                .filter(artifact -> "FIGMA_EXPORT_BUNDLE".equals(artifact.artifactType()))
                .map(artifact -> {
                    FigmaExportBundle bundle = artifactService.readFigmaExportBundle(artifact.uri());
                    return new OperationBundle(bundle.figmaScreenSpec().screenId(),
                            bundle.figmaScreenSpec().screenVersion(), bundle);
                }).toList();
    }

    public record OperationBundle(String screenId, int screenVersion, FigmaExportBundle bundle) {}

    /**
     * Plugin Apply 보고서 DTO (R5-041)
     */
    public record PluginApplyReport(
            String screenId,
            List<String> affectedNodeIds,
            int reuseCount,
            int createdCount,
            int fallbackCount,
            String summary
    ) {
    }
}
