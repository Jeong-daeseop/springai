package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import com.krdevops.springai.service.observability.PipelineMetricsCollector;
import com.krdevops.springai.service.pipeline.PipelineActionAuthorization;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import com.krdevops.springai.service.pipeline.PipelineOperationAuditService;
import com.krdevops.springai.service.pipeline.PipelineOperationGate;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PreviewEvidenceBundleRepositoryAuthorizationTest {
    @Test void saveAuthorized_requiresPreviewPermission() {
        var refs = List.of(new VersionedArtifactReference("ds", "DESIGN_SYSTEM", "1.0", "a".repeat(64), null),
                new VersionedArtifactReference("ui", "UI_DESIGN_SPEC", "2.0", "b".repeat(64), null),
                new VersionedArtifactReference("screen", "SCREEN_SPECIFICATION", "1.0", "c".repeat(64), null));
        var bundle = PreviewEvidenceBundle.builder("evidence", "op", "rev", "d".repeat(64))
                .references(refs).reports(new PreviewEvidenceBundle.Reports(null, null, null, null, null, null, null)).build();
        var gate = new PipelineOperationGate(new PipelineApiOperationCatalog(), new PipelineActionAuthorization(),
                new PipelineMetricsCollector(), new PipelineOperationAuditService());
        assertThatThrownBy(() -> new PreviewEvidenceBundleRepository().saveAuthorized(bundle, gate,
                PipelineActionAuthorization.AuthorizationContext.readOnly()))
                .isInstanceOf(IllegalStateException.class);
    }
    @org.junit.jupiter.api.Test void saveAuthorized_acceptsPreviewPermission() {
        var refs = List.of(new VersionedArtifactReference("ds", "DESIGN_SYSTEM", "1.0", "a".repeat(64), null),
                new VersionedArtifactReference("ui", "UI_DESIGN_SPEC", "2.0", "b".repeat(64), null),
                new VersionedArtifactReference("screen", "SCREEN_SPECIFICATION", "1.0", "c".repeat(64), null));
        var bundle = PreviewEvidenceBundle.builder("evidence-ok", "op", "rev", "d".repeat(64))
                .references(refs).reports(new PreviewEvidenceBundle.Reports(null, null, null, null, null, null, null)).build();
        var gate = new PipelineOperationGate(new PipelineApiOperationCatalog(), new PipelineActionAuthorization(),
                new PipelineMetricsCollector(), new PipelineOperationAuditService());
        var saved = new PreviewEvidenceBundleRepository().saveAuthorized(bundle, gate,
                PipelineActionAuthorization.AuthorizationContext.reviewer());
        org.assertj.core.api.Assertions.assertThat(saved.bundleId()).isEqualTo("evidence-ok");
    }
}
