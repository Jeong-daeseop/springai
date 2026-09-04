package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.config.PipelineEvolutionProperties;
import com.krdevops.springai.mapper.DesignCodeComponentMappingRepository;
import com.krdevops.springai.model.contract.VersionedArtifactReference;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpecV2;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignComponentRenderInput;
import com.krdevops.springai.service.UiDesignSpecArtifactReader;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** V2 Apply 직전 필수 Component Mapping과 Renderer 입력을 전부 확인하는 fail-closed Gate. */
@Service
public class RequiredComponentMappingApplyGate {

    public static final String THYMELEAF_KRDS_PROFILE = "thymeleaf-krds";
    private static final String MAPPING_ARTIFACT_TYPE = "DESIGN_CODE_COMPONENT_MAPPING";

    private final PipelineEvolutionProperties properties;
    private final UiDesignSpecArtifactReader artifactReader;
    private final DesignCodeComponentMappingRepository repository;
    private final ComponentFixtureModelAdapter fixtureAdapter;
    private final DesignComponentRenderInputService renderInputService;

    public RequiredComponentMappingApplyGate(
            PipelineEvolutionProperties properties,
            UiDesignSpecArtifactReader artifactReader,
            DesignCodeComponentMappingRepository repository,
            ComponentFixtureModelAdapter fixtureAdapter,
            DesignComponentRenderInputService renderInputService) {
        this.properties = properties;
        this.artifactReader = artifactReader;
        this.repository = repository;
        this.fixtureAdapter = fixtureAdapter;
        this.renderInputService = renderInputService;
    }

    public List<DesignComponentRenderInput> requireForApply(
            ScreenSpecification specification, String rendererProfile) {
        if (!properties.usesV2Apply()) return List.of();
        if (specification == null || specification.uiDesignSpecReference() == null) {
            throw new RequiredComponentMappingException(List.of(
                    "V2 Apply에는 UiDesignSpec Artifact 참조가 필요합니다."));
        }
        UiDesignSpecV2 spec = artifactReader
                .read(specification.uiDesignSpecReference().artifactId()).spec();
        LinkedHashMap<String, UiDesignSpecV2.ComponentReference> required = new LinkedHashMap<>();
        for (UiDesignSpecV2.SemanticNode node : spec.nodes()) {
            if (node.componentRef() != null) {
                String key = node.componentRef().logicalType() + "\u0000"
                        + node.componentRef().componentSetKey();
                required.putIfAbsent(key, node.componentRef());
            }
        }

        List<String> issues = new ArrayList<>();
        List<DesignComponentRenderInput> inputs = new ArrayList<>();
        for (UiDesignSpecV2.ComponentReference reference : required.values()) {
            DesignCodeComponentMapping mapping = repository.findApproved(
                            reference.logicalType(), reference.componentSetKey(), rendererProfile)
                    .orElse(null);
            if (mapping == null) {
                issues.add("승인 Mapping 누락: " + reference.logicalType() + "/"
                        + reference.componentSetKey() + " (" + rendererProfile + ")");
                continue;
            }
            validatePinnedReference(reference.mappingRef(), mapping, issues);
            var adaptation = fixtureAdapter.adapt(mapping);
            if (!adaptation.valid()) {
                adaptation.issues().stream()
                        .filter(issue -> issue.severity() == ComponentFixtureModelAdapter.Severity.ERROR)
                        .forEach(issue -> issues.add(mapping.mappingId() + ": " + issue.code()
                                + " - " + issue.message()));
                continue;
            }
            try {
                inputs.add(renderInputService.resolve(mapping, rendererProfile,
                        effectiveFigmaProperties(mapping, reference,
                                adaptation.fixture().figmaProperties()),
                        adaptation.fixture().figmaSlots()));
            } catch (ComponentVariantValueResolver.ComponentVariantResolutionException exception) {
                exception.resolution().issues().stream()
                        .filter(issue -> issue.severity() == ComponentVariantValueResolver.Severity.ERROR)
                        .forEach(issue -> issues.add(mapping.mappingId() + ": " + issue.code()
                                + " - " + issue.figmaProperty() + "=" + issue.rejectedValue()));
            } catch (RuntimeException exception) {
                issues.add(mapping.mappingId() + ": Renderer 입력 해석 실패 - "
                        + exception.getMessage());
            }
        }
        if (!issues.isEmpty()) throw new RequiredComponentMappingException(issues);
        return List.copyOf(inputs);
    }

    /**
     * Mapping의 시드 Fixture {@code figmaProperties} 위에 실제 Figma INSTANCE의
     * {@code componentProperties}(variant 값)를 덮어쓴다 — Mapping이 실제로 매핑하는
     * figmaProperty 키만 반영해 {@code UNMAPPED_FIGMA_PROPERTY} 잡음을 피한다. 인스턴스가
     * 지정하지 않은 속성은 Fixture 기본값(예: Label)을 그대로 쓴다.
     */
    private Map<String, Object> effectiveFigmaProperties(
            DesignCodeComponentMapping mapping,
            UiDesignSpecV2.ComponentReference reference,
            Map<String, Object> fixtureProperties) {
        LinkedHashMap<String, Object> effective = new LinkedHashMap<>(fixtureProperties);
        Map<String, String> instanceProperties = reference.componentProperties();
        if (instanceProperties.isEmpty()) return effective;
        java.util.Set<String> mappedKeys = mapping.propertyMappings().stream()
                .map(DesignCodeComponentMapping.PropertyMapping::figmaProperty)
                .collect(java.util.stream.Collectors.toSet());
        instanceProperties.forEach((key, value) -> {
            if (mappedKeys.contains(key) && value != null && !value.isBlank()) {
                effective.put(key, value);
            }
        });
        return effective;
    }

    private void validatePinnedReference(
            VersionedArtifactReference reference,
            DesignCodeComponentMapping mapping,
            List<String> issues) {
        if (reference == null) return;
        if (!MAPPING_ARTIFACT_TYPE.equals(reference.artifactType())
                || !mapping.mappingId().equals(reference.artifactId())
                || !mapping.version().equals(reference.schemaVersion())
                || !mapping.contentHash().equals(reference.contentHash())) {
            issues.add("Mapping 고정 참조 불일치: " + mapping.mappingId() + "@" + mapping.version());
        }
    }

    public static final class RequiredComponentMappingException extends IllegalStateException {
        private final List<String> issues;

        public RequiredComponentMappingException(List<String> issues) {
            super("필수 Component Mapping Apply Gate 실패: " + String.join("; ", issues));
            this.issues = List.copyOf(issues);
        }

        public List<String> issues() {
            return issues;
        }
    }
}
