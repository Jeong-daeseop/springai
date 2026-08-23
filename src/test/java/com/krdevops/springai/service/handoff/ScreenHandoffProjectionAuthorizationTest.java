package com.krdevops.springai.service.handoff;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import com.krdevops.springai.service.observability.PipelineMetricsCollector;
import com.krdevops.springai.service.pipeline.PipelineActionAuthorization;
import com.krdevops.springai.service.pipeline.PipelineApiOperationCatalog;
import com.krdevops.springai.service.pipeline.PipelineOperationAuditService;
import com.krdevops.springai.service.pipeline.PipelineOperationGate;
import com.krdevops.springai.model.contract.PipelineReleaseGateResponse;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreenHandoffProjectionAuthorizationTest {
    @Test void projectAuthorized_requiresReadPermission() {
        var auditHash = "0".repeat(64);
        var canonical = "handoff|op|rev|[]|[]|[issue]|[]|[]|" + auditHash;
        var hash = ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
        var bundle = new ScreenHandoffBundle("handoff", hash, "op", "rev", List.of(), List.of(),
                List.of("issue"), List.of(), List.of(), auditHash);
        var gate = new PipelineOperationGate(new PipelineApiOperationCatalog(), new PipelineActionAuthorization(),
                new PipelineMetricsCollector(), new PipelineOperationAuditService());
        assertThatThrownBy(() -> new ScreenHandoffProjectionService().projectAuthorized(bundle,
                ScreenHandoffProjectionService.Audience.AGENT, gate,
                new PipelineActionAuthorization.AuthorizationContext(false, false, false, false)))
                .isInstanceOf(IllegalStateException.class);
    }
    @org.junit.jupiter.api.Test void projectAuthorized_acceptsReadPermission() {
        var auditHash = "0".repeat(64);
        var canonical = "handoff-ok|op|rev|[]|[]|[]|[]|[]|" + auditHash;
        var hash = ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
        var bundle = new ScreenHandoffBundle("handoff-ok", hash, "op", "rev", List.of(), List.of(),
                List.of(), List.of(), List.of(), auditHash);
        var gate = new PipelineOperationGate(new PipelineApiOperationCatalog(), new PipelineActionAuthorization(),
                new PipelineMetricsCollector(), new PipelineOperationAuditService());
        var projection = new ScreenHandoffProjectionService().projectAuthorized(bundle,
                ScreenHandoffProjectionService.Audience.AGENT, gate,
                PipelineActionAuthorization.AuthorizationContext.readOnly());
        org.assertj.core.api.Assertions.assertThat(projection.bundleId()).isEqualTo("handoff-ok");
    }

    @org.junit.jupiter.api.Test void projection_includesReleaseGateAndAuditHash() {
        var auditHash = "0".repeat(64);
        var canonical = "handoff-projection|op|rev|[]|[]|[]|[]|[]|" + auditHash;
        var hash = ContentHashes.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
        var bundle = new ScreenHandoffBundle("handoff-projection", hash, "op", "rev", List.of(), List.of(), List.of(), List.of(), List.of(), auditHash);
        var response = new ScreenHandoffProjectionService().projectWithReleaseGate(bundle,
                ScreenHandoffProjectionService.Audience.AGENT,
                new PipelineReleaseGateResponse(false, java.util.Map.of("build", false), List.of("build")));
        org.assertj.core.api.Assertions.assertThat(response.releaseReady()).isFalse();
        org.assertj.core.api.Assertions.assertThat(response.failedGateNames()).containsExactly("build");
        org.assertj.core.api.Assertions.assertThat(response.auditSnapshotHash()).isEqualTo(auditHash);
    }
}
