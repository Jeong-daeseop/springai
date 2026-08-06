package com.krdevops.springai.service.artifact;

import com.krdevops.springai.config.ArtifactStoreProperties;
import com.krdevops.springai.config.OperationalResilienceProperties;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.artifact.StagedArtifact;
import com.krdevops.springai.service.observability.OperationalStatusService;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArtifactObservabilityTest {

    @TempDir
    Path root;

    @Test
    void 저장과_reconciliation을_metric과_운영상태에_기록한다() throws Exception {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);
        ArtifactStorePort store = mock(ArtifactStorePort.class);
        ArtifactCatalogPort catalog = mock(ArtifactCatalogPort.class);
        byte[] content = "artifact".getBytes();
        String hash = ContentHashes.sha256Hex(content);
        when(store.stage(content, "text/html"))
                .thenReturn(new StagedArtifact(root.resolve("staged"), hash, content.length, "text/html"));
        when(store.commit(any())).thenReturn("aa/bb/" + hash);
        when(catalog.save(any())).thenAnswer(invocation -> invocation.getArgument(0, Artifact.class));

        new ArtifactService(store, catalog, telemetry)
                .ingest(content, "text/html", "THYMELEAF_PREVIEW", "rev-1");

        ArtifactStoreProperties properties = new ArtifactStoreProperties();
        properties.setRootPath(root);
        when(catalog.findAll()).thenReturn(List.of());
        ExternalCallGuard guard = new ExternalCallGuard(new OperationalResilienceProperties());
        OperationalStatusService status = new OperationalStatusService(guard);
        new ArtifactReconciler(properties, catalog, store, telemetry, status).reconcile(true);

        assertThat(registry.get("springai.artifact.actions.total")
                .tag("artifact_type", "THYMELEAF_PREVIEW").tag("action", "INGEST")
                .tag("outcome", "SUCCESS").counter().count()).isEqualTo(1);
        assertThat(registry.get("springai.artifact.reconciliation.runs.total")
                .tag("mode", "DRY_RUN").counter().count()).isEqualTo(1);
        assertThat(status.snapshot().reconciler().state()).isEqualTo("COMPLETED");
    }
}

