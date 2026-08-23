package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.designsystem.ComponentFixtureModel;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.service.UiDesignSpecArtifactReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RequiredComponentMappingApplyGateTest {

    private final PipelineEvolutionProperties properties = new PipelineEvolutionProperties();
    private final UiDesignSpecArtifactReader reader = mock(UiDesignSpecArtifactReader.class);
    private final DesignCodeComponentMappingRepository repository =
            mock(DesignCodeComponentMappingRepository.class);
    private final UiDesignSpecArtifactReader.ReadResult readResult =
            mock(UiDesignSpecArtifactReader.ReadResult.class);
    private final RequiredComponentMappingApplyGate gate = new RequiredComponentMappingApplyGate(
            properties, reader, repository, new ComponentFixtureModelAdapter(),
            new DesignComponentRenderInputService(repository,
                    new ComponentPropertyParameterResolver(), new ComponentVariantValueResolver(),
                    new ComponentSlotRegionResolver()));

    @BeforeEach
    void enableV2Apply() {
        properties.setMode(PipelineEvolutionProperties.Mode.V2_APPLY);
    }

    @Test
    void 필수Component의승인Mapping과Fixture를Renderer입력으로확정한다() {
        DesignCodeComponentMapping mapping = mapping();
        when(reader.read("ui-spec-1")).thenReturn(readResult);
        when(readResult.spec()).thenReturn(uiSpec(null));
        when(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .thenReturn(Optional.of(mapping));

        var inputs = gate.requireForApply(screenSpecification(), "thymeleaf-krds");

        assertThat(inputs).singleElement().satisfies(input -> {
            assertThat(input.mappingId()).isEqualTo("map-button");
            assertThat(input.fragmentParameters()).containsEntry("variant", "primary")
                    .containsEntry("label", "저장");
            assertThat(input.fragmentRegions()).containsEntry("leadingIcon", "save");
        });
    }

    @Test
    void 승인Mapping누락은모든문제를모은뒤Apply를차단한다() {
        when(reader.read("ui-spec-1")).thenReturn(readResult);
        when(readResult.spec()).thenReturn(uiSpec(null));
        when(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> gate.requireForApply(screenSpecification(), "thymeleaf-krds"))
                .isInstanceOfSatisfying(
                        RequiredComponentMappingApplyGate.RequiredComponentMappingException.class,
                        exception -> assertThat(exception.issues())
                                .containsExactly("승인 Mapping 누락: button/FIGMA_BUTTON (thymeleaf-krds)"));
    }

    @Test
    void 고정MappingVersionHash가현재승인본과다르면차단한다() {
        VersionedArtifactReference wrong = new VersionedArtifactReference(
                "map-button", "DESIGN_CODE_COMPONENT_MAPPING", "2.0", "b".repeat(64), null);
        when(reader.read("ui-spec-1")).thenReturn(readResult);
        when(readResult.spec()).thenReturn(uiSpec(wrong));
        when(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .thenReturn(Optional.of(mapping()));

        assertThatThrownBy(() -> gate.requireForApply(screenSpecification(), "thymeleaf-krds"))
                .isInstanceOfSatisfying(
                        RequiredComponentMappingApplyGate.RequiredComponentMappingException.class,
                        exception -> assertThat(exception.issues())
                                .contains("Mapping 고정 참조 불일치: map-button@1.0"));
    }

    @Test
    void 미지원Variant는Parameter에서제거되고Fallback없이는Apply되지않는다() {
        when(reader.read("ui-spec-1")).thenReturn(readResult);
        when(readResult.spec()).thenReturn(uiSpec(null));
        when(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .thenReturn(Optional.of(mapping("Unknown", null)));

        assertThatThrownBy(() -> gate.requireForApply(screenSpecification(), "thymeleaf-krds"))
                .isInstanceOfSatisfying(
                        RequiredComponentMappingApplyGate.RequiredComponentMappingException.class,
                        exception -> assertThat(exception.issues())
                                .containsExactly("map-button: VARIANT_VALUE_UNSUPPORTED - Style=Unknown"));
    }

    @Test
    void 미지원Variant는명시적Fallback이있을때만Renderer입력에포함된다() {
        when(reader.read("ui-spec-1")).thenReturn(readResult);
        when(readResult.spec()).thenReturn(uiSpec(null));
        when(repository.findApproved("button", "FIGMA_BUTTON", "thymeleaf-krds"))
                .thenReturn(Optional.of(mapping("Unknown", "secondary")));

        var inputs = gate.requireForApply(screenSpecification(), "thymeleaf-krds");

        assertThat(inputs).singleElement().satisfies(input ->
                assertThat(input.fragmentParameters()).containsEntry("variant", "secondary"));
    }

    @Test
    void V2Apply이전모드에서는기존생성경로를변경하지않는다() {
        properties.setMode(PipelineEvolutionProperties.Mode.V2_PREVIEW);

        assertThat(gate.requireForApply(null, "thymeleaf-krds")).isEmpty();
        verifyNoInteractions(reader, repository);
    }

    private ScreenSpecification screenSpecification() {
        return new ScreenSpecification(
                "screen-1", 1, ScreenSpecStatus.APPROVED, "Q&A", "crud", "table",
                "egov", "QNA", List.of(), List.of(), List.of(),
                null, null, null, null, LocalDateTime.now(),
                new VersionedArtifactReference("ui-spec-1", "UI_DESIGN_SPEC_V2", "2.0",
                        "c".repeat(64), "figma-r1"), null);
    }

    private UiDesignSpecV2 uiSpec(VersionedArtifactReference mappingRef) {
        var evidence = new UiDesignSpecV2.InferenceEvidence(
                List.of("1:2"), 0.99, "figma-component", false, false);
        var component = new UiDesignSpecV2.ComponentReference(
                "button", "FIGMA_BUTTON", mappingRef);
        return new UiDesignSpecV2(
                "ui-spec-1", "2.0", "c".repeat(64),
                new UiDesignSpecV2.Source(
                        UiDesignSpecV2.SourceType.FIGMA, "file", "1:1", "figma-r1"),
                null,
                List.of(new UiDesignSpecV2.SemanticNode(
                        "submit", "action", "button", null, Map.of(), component,
                        List.of(), List.of(), evidence)),
                List.of(), List.of(), List.of(), List.of(), 0.99);
    }

    private DesignCodeComponentMapping mapping() {
        return mapping("Primary", null);
    }

    private DesignCodeComponentMapping mapping(String fixtureStyle, Object fallback) {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.APPROVED,
                "a".repeat(64), "button", "FIGMA_BUTTON", "fragments/button :: button",
                List.of(
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Style", "variant", Map.of("Primary", "primary"), true, null, fallback),
                        new DesignCodeComponentMapping.PropertyMapping(
                                "Label", "label", Map.of(), true, null, null)),
                List.of(new DesignCodeComponentMapping.SlotMapping(
                        "Leading icon", "leadingIcon")),
                Map.of(
                        "schemaVersion", ComponentFixtureModel.SCHEMA_VERSION,
                        "figmaProperties", Map.of("Style", fixtureStyle, "Label", "저장"),
                        "figmaSlots", Map.of("Leading icon", "save"),
                        "contextVariables", Map.of()),
                List.of("thymeleaf-krds"), "figma-r1", "reviewer",
                Instant.parse("2026-08-23T01:00:00Z"));
    }
}
