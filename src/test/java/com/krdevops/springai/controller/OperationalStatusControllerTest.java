package com.krdevops.springai.controller;

import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.service.artifact.ArtifactReconciler;
import com.krdevops.springai.service.observability.OperationalStatusService;
import com.krdevops.springai.service.observability.PipelineInfrastructureSmokeService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;
import org.springframework.test.web.servlet.MockMvc;

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

    @Test
    void infrastructureSmoke는_DB와_Redis_상태를_반환한다() {
        PipelineInfrastructureSmokeService smoke = mock(PipelineInfrastructureSmokeService.class);
        PipelineInfrastructureSmokeService.SmokeReport report =
                new PipelineInfrastructureSmokeService.SmokeReport(true, true, "OK");
        when(smoke.check()).thenReturn(report);
        OperationalStatusController controller = new OperationalStatusController(
                mock(OperationalStatusService.class), mock(ArtifactReconciler.class), smoke);

        assertThat(controller.infrastructureSmoke()).isSameAs(report);
        verify(smoke).check();
    }

    @Test
    void infrastructureSmokeEndpoint는_운영상태를_JSON으로_노출한다() throws Exception {
        PipelineInfrastructureSmokeService smoke = mock(PipelineInfrastructureSmokeService.class);
        when(smoke.check()).thenReturn(new PipelineInfrastructureSmokeService.SmokeReport(true, false, "Redis 연결 실패"));
        MockMvc mvc = standaloneSetup(new OperationalStatusController(
                mock(OperationalStatusService.class), mock(ArtifactReconciler.class), smoke)).build();

        mvc.perform(get("/api/operations/infrastructure-smoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.databaseReady").value(true))
                .andExpect(jsonPath("$.redisReady").value(false))
                .andExpect(jsonPath("$.message").value("Redis 연결 실패"));
    }
}
