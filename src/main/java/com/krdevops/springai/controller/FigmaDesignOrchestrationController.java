package com.krdevops.springai.controller;

import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.figma.FigmaDesignOrchestrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 승인된 ScreenSpecification → Bundle → Artifact의 X-API-Key 보호 REST 진입점. */
@RestController
@RequestMapping("/api/figma/orchestration")
@RequiredArgsConstructor
public class FigmaDesignOrchestrationController {
    private final FigmaDesignOrchestrationService orchestrationService;

    @PostMapping("/approved-specification")
    public FigmaDesignOperation createFromApprovedSpecification(@RequestBody Request request) {
        return orchestrationService.processApprovedSpecificationRequest(
                request.designRequest(), request.exportRequest());
    }

    /**
     * 22/23번 문서 A-02: {@code bindFigmaDesignRequestTable} MCP Tool과 동일한 동작을 REST로도
     * 제공한다(선택 항목). {@code AWAITING_TABLE_BINDING} 상태가 아닌 Operation에 호출하거나
     * operationId가 존재하지 않으면 400으로 거부한다.
     */
    @PostMapping("/bind-table")
    public FigmaDesignOperation bindTable(@RequestBody BindTableRequest request) {
        try {
            return orchestrationService.bindTable(
                    request.operationId(), request.database(), request.tableName());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new FigmaRequestException("FIGMA_OPERATION_TABLE_BINDING_FAILED", e.getMessage());
        }
    }

    public record Request(FigmaDesignRequest designRequest, FigmaScreenExportRequest exportRequest) {}

    public record BindTableRequest(String operationId, String database, String tableName) {}
}
