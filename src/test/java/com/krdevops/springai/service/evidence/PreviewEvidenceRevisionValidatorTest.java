package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewEvidenceRevisionValidatorTest {
    @Test
    void Reference의_sourceRevision이_bundle과_다르면_차단한다() {
        var ref = new VersionedArtifactReference("ui", "UI", "1.0", "a".repeat(64), "r2");
        var bundle = PreviewEvidenceBundle.builder("b1", "op1", "r1", "b".repeat(64))
                .references(List.of(ref, ref, ref))
                .reports(new PreviewEvidenceBundle.Reports(null, null, null, null, null, null, null)).build();
        var result = new PreviewEvidenceRevisionValidator().validate(bundle, "op1", "r1");
        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).contains("EVIDENCE_REFERENCE_REVISION_MISMATCH:ui");
    }
}
