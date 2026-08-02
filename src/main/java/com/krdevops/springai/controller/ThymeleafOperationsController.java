package com.krdevops.springai.controller;

import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

/** X-API-Key로 보호되는 Thymeleaf Preview/Approve/Apply/Report/Revalidate REST 계약. */
@RestController
@RequestMapping("/api/thymeleaf/operations")
@RequiredArgsConstructor
public class ThymeleafOperationsController {
    private final ThymeleafProjectWorkflowService workflow;

    @PostMapping("/preview")
    public ThymeleafProjectWorkflowService.WorkflowResult preview(@RequestBody PreviewRequest request) {
        return workflow.preview(Path.of(request.projectRoot()), request.generatedFiles());
    }

    @PostMapping("/{operationId}/approve")
    public ThymeleafProjectWorkflowService.WorkflowResult approve(
            @PathVariable String operationId, @RequestBody ApprovalRequest request) {
        return workflow.approve(operationId, request.previewHash());
    }

    @PostMapping("/{operationId}/apply")
    public ThymeleafProjectWorkflowService.WorkflowResult apply(@PathVariable String operationId) {
        return workflow.apply(operationId);
    }

    @PostMapping("/{operationId}/revalidate")
    public ThymeleafProjectWorkflowService.WorkflowResult revalidate(@PathVariable String operationId) {
        return workflow.revalidate(operationId);
    }

    @GetMapping("/{operationId}/report")
    public ResponseEntity<ThymeleafProjectWorkflowService.WorkflowResult> report(@PathVariable String operationId) {
        return workflow.find(operationId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PreviewRequest(String projectRoot, Map<String, String> generatedFiles) {}
    public record ApprovalRequest(String previewHash) {}
}
