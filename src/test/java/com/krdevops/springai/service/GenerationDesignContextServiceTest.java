package com.krdevops.springai.service;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerationDesignContextServiceTest {

    private final DesignReferenceAnalysisService analysisService = mock(DesignReferenceAnalysisService.class);
    private final ScreenSpecificationService specificationService = mock(ScreenSpecificationService.class);
    private final GenerationDesignContextService service =
            new GenerationDesignContextService(analysisService, specificationService);

    @Test
    void resolvesApprovedSpecification() {
        ScreenSpecification approved = specification(ScreenSpecStatus.APPROVED);
        when(specificationService.get("spec-1")).thenReturn(approved);
        when(specificationService.revalidate(approved)).thenReturn(approved);

        assertThat(service.resolve("com", "LETTNBBS", "공지", "board", null, "spec-1"))
                .isSameAs(approved);
    }

    @Test
    void reviewRequiredSpecificationBlocksGeneration() {
        ScreenSpecification reviewRequired = specification(ScreenSpecStatus.REVIEW_REQUIRED);
        when(specificationService.get("spec-1")).thenReturn(reviewRequired);
        when(specificationService.revalidate(reviewRequired)).thenReturn(reviewRequired);

        assertThatThrownBy(() -> service.resolve(
                "com", "LETTNBBS", "공지", "board", null, "spec-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APPROVED");
    }

    @Test
    void designReference_V2Preview에서_v2_Artifact를_영속화하고_참조를_고정한다() {
        UiDesignSpecV1ToV2Adapter adapter = new UiDesignSpecV1ToV2Adapter();
        UiDesignSpecV2ArtifactWriter writer = mock(UiDesignSpecV2ArtifactWriter.class);
        DesignContextArtifactReferenceValidator validator =
                mock(DesignContextArtifactReferenceValidator.class);
        PipelineEvolutionProperties props = new PipelineEvolutionProperties();
        props.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);
        GenerationDesignContextService svc = new GenerationDesignContextService(
                analysisService, specificationService, props, validator, adapter, writer);

        DesignAnalysisResult analysis = new DesignAnalysisResult(
                "an-1", "hash-1", "/tmp/ref.png", null, DesignSourceType.FILE, null,
                "v1", UiDesignSpec.SCHEMA_VERSION, "crud", "openai", "gpt-4o-mini", "v1",
                List.of(), UiDesignSpec.empty("CRUD_LIST"), List.of(), LocalDateTime.now(), null);
        VersionedArtifactReference designRef = new VersionedArtifactReference(
                "an-1", "UI_DESIGN_SPEC_V2", "2.0", "b".repeat(64), "hash-1");
        ScreenSpecification approved = new ScreenSpecification(
                "spec-x", 1, ScreenSpecStatus.APPROVED, "공지", "crud", "CRUD_LIST",
                "com", "LETTNBBS", List.of(), List.of(), List.of(), LocalDateTime.now())
                .withDesignContext(designRef, null, List.of());

        when(analysisService.get("an-1")).thenReturn(analysis);
        when(writer.write(any())).thenReturn(designRef);
        when(specificationService.createFromV2(eq("com"), eq("LETTNBBS"), eq("공지"), eq("crud"),
                any(), eq(designRef), isNull(), isNull())).thenReturn(approved);
        when(specificationService.revalidate(approved)).thenReturn(approved);

        ScreenSpecification result = svc.resolve("com", "LETTNBBS", "공지", "crud", "an-1", null);

        assertThat(result.uiDesignSpecReference()).isEqualTo(designRef);
        verify(writer).write(any());
        verify(validator).requireActiveExact(designRef);
    }

    @Test
    void designReference_Disabled에서는_기존_v1_create_경로를_유지한다() {
        ScreenSpecification approved = specification(ScreenSpecStatus.APPROVED);
        DesignAnalysisResult analysis = new DesignAnalysisResult(
                "an-1", "hash-1", "/tmp/ref.png", null, DesignSourceType.FILE, null,
                "v1", UiDesignSpec.SCHEMA_VERSION, "crud", "openai", "gpt-4o-mini", "v1",
                List.of(), UiDesignSpec.empty("CRUD_LIST"), List.of(), LocalDateTime.now(), null);
        when(analysisService.get("an-1")).thenReturn(analysis);
        when(specificationService.create("com", "LETTNBBS", "공지", "board", analysis.uiSpec()))
                .thenReturn(approved);
        when(specificationService.revalidate(approved)).thenReturn(approved);

        assertThat(service.resolve("com", "LETTNBBS", "공지", "board", "an-1", null))
                .isSameAs(approved);
    }

    private ScreenSpecification specification(ScreenSpecStatus status) {
        return new ScreenSpecification("spec-1", 1, status, "공지", "board", "BOARD",
                "com", "LETTNBBS", List.of(), List.of(), List.of(), LocalDateTime.now());
    }
}
