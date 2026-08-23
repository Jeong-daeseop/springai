package com.krdevops.springai.service.e2e;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import com.krdevops.springai.model.handoff.ScreenHandoffBundle;
import com.krdevops.springai.service.evidence.PreviewEvidenceBundleRepository;
import com.krdevops.springai.service.handoff.ScreenHandoffBundleRepository;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Preview Evidence가 Handoff로 전달될 때 hash와 승인 판정이 보존되는지 검증한다. */
class PipelineEvidenceHandoffIntegrationTest {
    @Test
    void previewEvidence_to_handoff_preservesAuditAndReleaseDecision() {
        String auditHash = "a".repeat(64);
        VersionedArtifactReference figma = reference("figma-spec", "UI_DESIGN_SPEC", auditHash);
        VersionedArtifactReference thymeleaf = reference("thymeleaf-view", "THYMELEAF", "b".repeat(64));
        var reports = new PreviewEvidenceBundle.Reports(thymeleaf, thymeleaf, thymeleaf, null, null, null, null);
        var evidence = PreviewEvidenceBundle.builder("evidence-e2e", "operation-e2e", "rev-1", auditHash)
                .references(List.of(figma, thymeleaf, reference("screen-contract", "SCREEN_SPECIFICATION", "c".repeat(64))))
                .artifacts(List.of(thymeleaf))
                .reports(reports)
                .finalDecision(PreviewEvidenceBundle.FinalDecision.PASS)
                .auditSnapshotHash(auditHash)
                .build();
        var evidenceStore = new PreviewEvidenceBundleRepository();
        evidenceStore.save(evidence);

        String handoffId = "handoff-e2e";
        String handoffCanonical = handoffId + "|operation-e2e|rev-1|[" + figma + "]|[]|[]|[]|[]|" + auditHash;
        String handoffHash = ContentHashes.sha256Hex(handoffCanonical.getBytes(StandardCharsets.UTF_8));
        var handoff = new ScreenHandoffBundle(handoffId, handoffHash, "operation-e2e", "rev-1",
                List.of(figma), List.of(), List.of(), List.of(), List.of(), auditHash);
        var handoffStore = new ScreenHandoffBundleRepository();
        handoffStore.save(handoff);

        var readiness = new PipelineReleaseReadiness().evaluate(Map.of("binding", true, "build", true, "render", true));
        assertThat(evidenceStore.find("evidence-e2e")).contains(evidence);
        assertThat(handoffStore.find(handoffId)).contains(handoff);
        assertThat(readiness.ready()).isTrue();
        assertThat(handoff.auditSnapshotHash()).isEqualTo(evidence.auditSnapshotHash());
    }

    private VersionedArtifactReference reference(String id, String type, String hash) {
        return new VersionedArtifactReference(id, type, "1.0", hash, "rev-1");
    }
}
