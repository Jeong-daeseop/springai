package com.krdevops.springai.controller;

import com.krdevops.springai.model.thymeleaf.BaselineApprovalRequest;
import com.krdevops.springai.model.thymeleaf.BaselineApprovalResult;
import com.krdevops.springai.service.thymeleaf.BaselineApprovalService;
import com.krdevops.springai.service.thymeleaf.ThymeleafProjectWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** X-API-Key로 보호되는 Thymeleaf Preview/Approve/Apply/Report/Revalidate REST 계약. */
@RestController
@RequestMapping("/api/thymeleaf/operations")
@RequiredArgsConstructor
public class ThymeleafOperationsController {
    private final ThymeleafProjectWorkflowService workflow;
    private final BaselineApprovalService baselineApproval;

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

    /** 바디를 생략하면 브라우저 Gate 없이 기존 정적 재검증만 수행한다(하위 호환). */
    @PostMapping("/{operationId}/revalidate")
    public ThymeleafProjectWorkflowService.WorkflowResult revalidate(
            @PathVariable String operationId,
            @RequestBody(required = false) RevalidateRequestBody body) {
        return workflow.revalidate(operationId, body == null ? null : body.browserOptions());
    }

    @PostMapping("/{operationId}/baseline-approvals")
    public BaselineApprovalResult approveBaseline(
            @PathVariable String operationId, @RequestBody BaselineApprovalBody body) {
        return baselineApproval.approve(new BaselineApprovalRequest(
                operationId, body.screenId(), body.relativeFile(), body.url(), body.renderedHtml(),
                body.viewports(), body.maskSelectors(), body.readySelector()));
    }

    @GetMapping("/{operationId}/report")
    public ResponseEntity<ThymeleafProjectWorkflowService.WorkflowResult> report(@PathVariable String operationId) {
        return workflow.find(operationId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PreviewRequest(String projectRoot, Map<String, String> generatedFiles) {}
    public record ApprovalRequest(String previewHash) {}

    public record RevalidateRequestBody(
            ThymeleafProjectWorkflowService.RevalidateBrowserOptions browserOptions) {}

    /** operationId는 경로변수로 받으므로 body에 두지 않는다. */
    public record BaselineApprovalBody(
            String screenId, String relativeFile, String url, String renderedHtml,
            List<String> viewports, List<String> maskSelectors, String readySelector) {}
}
