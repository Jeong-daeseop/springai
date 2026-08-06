package com.krdevops.springai.service.observability;

import com.krdevops.springai.config.OperationalResilienceProperties;
import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.krdevops.springai.service.resilience.ExternalDependency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalStatusServiceTest {

    @Test
    void outbox_미구현과_최근_reconciler_circuit_상태를_숨김없이_반환한다() {
        OperationalResilienceProperties properties = new OperationalResilienceProperties();
        properties.getCircuitBreaker().setFailureThreshold(1);
        ExternalCallGuard guard = new ExternalCallGuard(properties);
        OperationalStatusService service = new OperationalStatusService(guard);
        assertThatThrownBy(() -> guard.execute(ExternalDependency.FIGMA, () -> {
            throw new IllegalStateException("injected");
        })).isInstanceOf(IllegalStateException.class);
        service.record(new ArtifactReconciliationReport(true,
                List.of("a".repeat(64)), List.of(), List.of()));

        var status = service.snapshot();

        assertThat(status.outbox().state()).isEqualTo("NOT_CONFIGURED");
        assertThat(status.reconciler().state()).isEqualTo("COMPLETED");
        assertThat(status.reconciler().dryRun()).isTrue();
        assertThat(status.reconciler().orphanCount()).isEqualTo(1);
        assertThat(status.circuits().get(ExternalDependency.FIGMA)).isEqualTo("OPEN");
    }
}

