package com.krdevops.springai.service.observability;

import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.krdevops.springai.service.resilience.ExternalDependency;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class OperationalStatusService {

    private final ExternalCallGuard externalCallGuard;
    private final AtomicReference<ReconcilerStatus> reconciler =
            new AtomicReference<>(new ReconcilerStatus("NEVER_RUN", null, true, 0, 0, 0));

    public OperationalStatusService(ExternalCallGuard externalCallGuard) {
        this.externalCallGuard = externalCallGuard;
    }

    public void record(ArtifactReconciliationReport report) {
        reconciler.set(new ReconcilerStatus("COMPLETED", Instant.now(), report.dryRun(),
                report.orphanContentHashes().size(), report.missingArtifacts().size(),
                report.quarantinedContentHashes().size()));
    }

    public OperationalStatus snapshot() {
        Map<ExternalDependency, String> circuits = new EnumMap<>(ExternalDependency.class);
        externalCallGuard.snapshots().forEach((dependency, snapshot) ->
                circuits.put(dependency, snapshot.state().name()));
        return new OperationalStatus(Instant.now(),
                new OutboxStatus("NOT_CONFIGURED",
                        "ARCH-0508 outbox는 미구현이며 poll 기반 ArtifactReconciler를 사용합니다."),
                reconciler.get(), Map.copyOf(circuits));
    }

    public record OperationalStatus(Instant observedAt, OutboxStatus outbox,
                                    ReconcilerStatus reconciler, Map<ExternalDependency, String> circuits) { }
    public record OutboxStatus(String state, String detail) { }
    public record ReconcilerStatus(String state, Instant lastRunAt, boolean dryRun,
                                   int orphanCount, int missingCount, int quarantinedCount) { }
}

