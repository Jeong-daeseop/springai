package com.krdevops.springai.controller;

import com.krdevops.springai.service.evidence.PreviewEvidenceBundleRepository;
import com.krdevops.springai.service.handoff.ScreenHandoffBundleRepository;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PipelineArtifactControllerTest {
    @Test void missingEvidenceAndHandoff_areNotFound() {
        var controller = new PipelineArtifactController(new PreviewEvidenceBundleRepository(), new ScreenHandoffBundleRepository());
        assertThatThrownBy(() -> controller.evidence("missing")).hasMessageContaining("404");
        assertThatThrownBy(() -> controller.handoff("missing")).hasMessageContaining("404");
    }
}
