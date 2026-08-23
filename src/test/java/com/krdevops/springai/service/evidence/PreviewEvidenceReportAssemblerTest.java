package com.krdevops.springai.service.evidence;

import com.krdevops.springai.model.contract.VersionedArtifactReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PreviewEvidenceReportAssemblerTest {
    @Test void 필수_Report와_optional_Report를_조립한다() {
        var ref = new VersionedArtifactReference("x", "REPORT", "1.0", "a".repeat(64), null);
        var reports = new PreviewEvidenceReportAssembler().reports(ref, null, ref, ref, ref, ref, null);
        assertThat(reports.hasRequired()).isTrue();
        assertThat(new PreviewEvidenceReportAssembler().aggregateFallbacks(java.util.List.of("warn"), java.util.List.of("warn", "fallback")))
                .containsExactly("fallback", "warn");
    }
}
