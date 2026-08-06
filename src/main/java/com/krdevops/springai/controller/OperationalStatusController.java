package com.krdevops.springai.controller;

import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.service.artifact.ArtifactReconciler;
import com.krdevops.springai.service.observability.OperationalStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/operations")
public class OperationalStatusController {

    private final OperationalStatusService statusService;
    private final ArtifactReconciler reconciler;

    public OperationalStatusController(OperationalStatusService statusService, ArtifactReconciler reconciler) {
        this.statusService = statusService;
        this.reconciler = reconciler;
    }

    @GetMapping("/status")
    public OperationalStatusService.OperationalStatus status() {
        return statusService.snapshot();
    }

    @PostMapping("/artifacts/reconcile/dry-run")
    public ArtifactReconciliationReport dryRun() {
        return reconciler.reconcile(true);
    }

    @PostMapping("/artifacts/reconcile")
    @ResponseStatus(HttpStatus.OK)
    public ArtifactReconciliationReport execute(@RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "실제 quarantine 실행에는 confirm=true가 필요합니다.");
        }
        return reconciler.reconcile(false);
    }
}
