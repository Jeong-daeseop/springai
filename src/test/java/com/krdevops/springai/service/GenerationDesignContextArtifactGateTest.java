package com.krdevops.springai.service;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationDesignContextArtifactGateTest {

    private final DesignReferenceAnalysisService analysisService = mock(DesignReferenceAnalysisService.class);
    private final ScreenSpecificationService specificationService = mock(ScreenSpecificationService.class);
    private final DesignContextArtifactReferenceValidator artifactValidator =
            mock(DesignContextArtifactReferenceValidator.class);

    @Test
    void Disabled에서는_Legacy_명세를_기존처럼_허용한다() {
        PipelineEvolutionProperties properties = properties(PipelineEvolutionProperties.Mode.DISABLED);
        ScreenSpecification legacy = specification(null);
        stub(legacy);

        ScreenSpecification resolved = service(properties).resolve(
                "com", "LETTNBBS", "공지", "board", null, "spec-1");

        assertThat(resolved).isSameAs(legacy);
    }

    @Test
    void DualRead에서는_참조가_있을_때_정확성을_재검증한다() {
        PipelineEvolutionProperties properties = properties(PipelineEvolutionProperties.Mode.DUAL_READ);
        VersionedArtifactReference reference = reference();
        ScreenSpecification specification = specification(reference);
        stub(specification);

        service(properties).resolve("com", "LETTNBBS", "공지", "board", null, "spec-1");

        verify(artifactValidator).requireActiveExact(reference);
    }

    @Test
    void V2Apply에서는_Legacy_명세를_차단한다() {
        PipelineEvolutionProperties properties = properties(PipelineEvolutionProperties.Mode.V2_APPLY);
        ScreenSpecification legacy = specification(null);
        stub(legacy);

        assertThatThrownBy(() -> service(properties).resolve(
                "com", "LETTNBBS", "공지", "board", null, "spec-1"))
                .isInstanceOf(DesignContextArtifactReferenceValidator.DesignContextArtifactException.class)
                .hasMessageContaining("UiDesignSpec Artifact 참조");
    }

    private GenerationDesignContextService service(PipelineEvolutionProperties properties) {
        return new GenerationDesignContextService(
                analysisService, specificationService, properties, artifactValidator);
    }

    private PipelineEvolutionProperties properties(PipelineEvolutionProperties.Mode mode) {
        PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
        properties.setMode(mode);
        return properties;
    }

    private void stub(ScreenSpecification specification) {
        when(specificationService.get("spec-1")).thenReturn(specification);
        when(specificationService.revalidate(specification)).thenReturn(specification);
    }

    private VersionedArtifactReference reference() {
        return new VersionedArtifactReference(
                "ui-1", "UI_DESIGN_SPEC_V2", "2.0", "a".repeat(64), "r1");
    }

    private ScreenSpecification specification(VersionedArtifactReference reference) {
        ScreenSpecification base = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "공지", "board", "BOARD",
                "com", "LETTNBBS", List.of(), List.of(), List.of(), LocalDateTime.now());
        return reference == null ? base : base.withDesignContext(reference, null, List.of());
    }
}
