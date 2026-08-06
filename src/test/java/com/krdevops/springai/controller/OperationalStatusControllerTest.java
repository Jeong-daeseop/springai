package com.krdevops.springai.controller;

import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.service.artifact.ArtifactReconciler;
import com.krdevops.springai.service.observability.OperationalStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalStatusControllerTest {

    @Test
    void dryRun은_변경없는_reconciliation만_실행한다() {
        ArtifactReconciler reconciler = mock(ArtifactReconciler.class);
        ArtifactReconciliationReport report = new ArtifactReconciliationReport(true, List.of(), List.of(), List.of());
        when(reconciler.reconcile(true)).thenReturn(report);
        OperationalStatusController controller =
                new OperationalStatusController(mock(OperationalStatusService.class), reconciler);

        assertThat(controller.dryRun()).isSameAs(report);
        verify(reconciler).reconcile(true);
        verify(reconciler, never()).reconcile(false);
    }

    @Test
    void 실제_조정은_confirm_true가_없으면_거부한다() {
        ArtifactReconciler reconciler = mock(ArtifactReconciler.class);
        OperationalStatusController controller =
                new OperationalStatusController(mock(OperationalStatusService.class), reconciler);

        assertThatThrownBy(() -> controller.execute(false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(reconciler, never()).reconcile(false);
    }

    @Test
    void confirm_true이면_quarantine_reconciliation을_실행한다() {
        ArtifactReconciler reconciler = mock(ArtifactReconciler.class);
        ArtifactReconciliationReport report = new ArtifactReconciliationReport(false, List.of(), List.of(), List.of());
        when(reconciler.reconcile(false)).thenReturn(report);
        OperationalStatusController controller =
                new OperationalStatusController(mock(OperationalStatusService.class), reconciler);

        assertThat(controller.execute(true)).isSameAs(report);
        verify(reconciler).reconcile(false);
    }
}
