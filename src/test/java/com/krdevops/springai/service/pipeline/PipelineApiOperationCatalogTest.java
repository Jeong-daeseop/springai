package com.krdevops.springai.service.pipeline;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineApiOperationCatalogTest {
    private final PipelineApiOperationCatalog catalog = new PipelineApiOperationCatalog();

    @Test
    void operationCatalog_isImmutableAndResolvable() {
        assertThat(catalog.operations()).hasSize(7);
        assertThat(catalog.require("getScreenHandoff").risk()).isEqualTo("READ");
        assertThat(catalog.contains("retryGenerationJob")).isTrue();
        assertThat(catalog.contains("unknown")).isFalse();
        assertThat(catalog.operationsByRisk("PREVIEW")).extracting(PipelineApiOperationCatalog.Operation::name)
                .containsExactly("previewComponentMapping", "previewGenerationScope");
        assertThat(catalog.supportedRisks()).containsExactlyInAnyOrder("READ", "PREVIEW", "REVIEW", "APPROVE", "RETRY");
        assertThat(catalog.operationNames()).isSorted().hasSize(7);
        assertThat(catalog.groupedByRisk().get("READ")).hasSize(2);
        assertThatThrownBy(() -> catalog.require("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotHash_isStableForSameCatalog() {
        ToolDefinitionSnapshotService service = new ToolDefinitionSnapshotService();
        assertThat(service.snapshotHash(catalog)).isEqualTo(service.snapshotHash(catalog));
        var snapshot = service.snapshot(catalog);
        assertThat(snapshot.operations()).hasSize(7);
        assertThat(snapshot.hash()).isEqualTo(service.snapshotHash(catalog));
        assertThat(service.matches(catalog, snapshot)).isTrue();
        assertThat(service.matches(catalog, new ToolDefinitionSnapshotService.Snapshot(
                snapshot.operations(), "different"))).isFalse();
    }

    @Test
    void authorization_isDeniedByDefaultForMutatingOperations() {
        PipelineActionAuthorization authorization = new PipelineActionAuthorization();
        var preview = catalog.require("previewGenerationScope");
        var approve = catalog.require("publishDesignSystemSnapshot");

        authorization.requireOperation(preview, PipelineActionAuthorization.AuthorizationContext.reviewer());
        assertThatThrownBy(() -> authorization.requireOperation(approve,
                PipelineActionAuthorization.AuthorizationContext.reviewer()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Apply");
    }

    @Test
    void operationGate_resolvesAndAuthorizesInOneStep() {
        var metrics = new com.krdevops.springai.service.observability.PipelineMetricsCollector();
        var audit = new PipelineOperationAuditService();
        PipelineOperationGate gate = new PipelineOperationGate(
                catalog, new PipelineActionAuthorization(), metrics, audit);
        assertThat(gate.authorize("getPreviewEvidence",
                PipelineActionAuthorization.AuthorizationContext.readOnly()).name())
                .isEqualTo("getPreviewEvidence");
        assertThatThrownBy(() -> gate.authorize("reviewSessionDecision",
                PipelineActionAuthorization.AuthorizationContext.readOnly()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(metrics.count("pipeline_operation_authorized")).isEqualTo(1);
        assertThat(metrics.count("pipeline_operation_denied")).isEqualTo(1);
        assertThat(audit.all()).hasSize(2);
        assertThat(audit.all().get(1).authorized()).isFalse();
        assertThat(audit.findByOperation("getPreviewEvidence")).hasSize(1);
        assertThat(audit.recent(1)).singleElement()
                .extracting(PipelineOperationAuditService.Entry::authorized).isEqualTo(false);
        assertThat(audit.snapshotHash()).isNotBlank();
        assertThat(audit.countAuthorized()).isEqualTo(1);
        assertThat(audit.countDenied()).isEqualTo(1);
        assertThat(audit.countByRisk("READ")).isEqualTo(1);
        assertThat(audit.countByOperation("getPreviewEvidence")).isEqualTo(1);
        assertThat(audit.countAuthorizedByOperation("getPreviewEvidence")).isEqualTo(1);
        assertThat(audit.countDeniedByOperation("reviewSessionDecision")).isEqualTo(1);
    }

    @Test
    void operationGate_auditsUnknownOperationAsDenied() {
        var audit = new PipelineOperationAuditService();
        var gate = new PipelineOperationGate(catalog, new PipelineActionAuthorization(),
                new com.krdevops.springai.service.observability.PipelineMetricsCollector(), audit);
        assertThatThrownBy(() -> gate.authorize("deleteEverything",
                PipelineActionAuthorization.AuthorizationContext.approver()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(audit.findByOperation("deleteEverything")).singleElement()
                .satisfies(entry -> {
                    assertThat(entry.risk()).isEqualTo("UNKNOWN");
                    assertThat(entry.authorized()).isFalse();
                });
    }

    @Test
    void auditHistory_isBounded() {
        var audit = new PipelineOperationAuditService(2);
        audit.record("one", "READ", true);
        audit.record("two", "READ", true);
        audit.record("three", "READ", false);
        assertThat(audit.all()).extracting(PipelineOperationAuditService.Entry::operation)
                .containsExactly("two", "three");
    }

    @Test
    void snapshotHash_ignoresRegistrationOrder() {
        var reversed = new PipelineApiOperationCatalog() {
            @Override
            public java.util.List<Operation> operations() {
                var values = new java.util.ArrayList<>(super.operations());
                java.util.Collections.reverse(values);
                return values;
            }
        };
        var service = new ToolDefinitionSnapshotService();
        assertThat(service.snapshotHash(catalog)).isEqualTo(service.snapshotHash(reversed));
    }

    @Test
    void operationDefinition_rejectsUnknownRisk() {
        assertThatThrownBy(() -> new PipelineApiOperationCatalog.Operation("custom", "DELETE"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("risk");
    }
}
