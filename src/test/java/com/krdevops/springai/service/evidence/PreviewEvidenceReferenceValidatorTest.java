package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.evidence.PreviewEvidenceBundle;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewEvidenceReferenceValidatorTest {
    @Test
    void 세_핵심_계약_reference를_확인한다() {
        var refs = List.of(new VersionedArtifactReference("ds", "DESIGN_SYSTEM", "1.0", "a".repeat(64), null),
                new VersionedArtifactReference("ui", "UI_DESIGN_SPEC", "2.0", "b".repeat(64), null),
                new VersionedArtifactReference("screen", "SCREEN_SPECIFICATION", "1.0", "c".repeat(64), null));
        var bundle = PreviewEvidenceBundle.builder("b", "op", "r", "d".repeat(64)).references(refs)
                .reports(new PreviewEvidenceBundle.Reports(null, null, null, null, null, null, null)).build();
        assertThat(new PreviewEvidenceReferenceValidator().validate(bundle).valid()).isTrue();
    }
}
