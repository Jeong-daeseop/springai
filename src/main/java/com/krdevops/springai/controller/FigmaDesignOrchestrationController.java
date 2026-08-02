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

    public record Request(FigmaDesignRequest designRequest, FigmaScreenExportRequest exportRequest) {}
}
