package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catalog 논리 계약·승인 Registry Binding·Code Mapping의 일관성을 한 Gate에서 검증한다. */
@Service
public class DesignCodeComponentMappingCrossValidator {

    private final ComponentRegistryBindingValidator registryValidator;

    public DesignCodeComponentMappingCrossValidator(ComponentRegistryBindingValidator registryValidator) {
        this.registryValidator = registryValidator;
    }

    public ValidationResult validate(
            ComponentCatalog catalog,
            String catalogHash,
            ComponentRegistrySnapshotV3 registry,
            DesignCodeComponentMapping mapping,
            String rendererProfile) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (catalog == null) {
            issues.add(error("CATALOG_NULL", "Component Catalog가 없습니다.", null));
            if (registry == null) {
                issues.add(error("REGISTRY_NULL", "Component Registry Snapshot이 없습니다.", null));
            }
        } else {
            issues.addAll(registryValidator.validate(catalog, catalogHash, registry, true));
        }
        if (mapping == null) {
            issues.add(error("COMPONENT_MAPPING_NULL", "Component Mapping이 없습니다.", null));
            return new ValidationResult(null, null, issues);
        }
        if (catalog == null || registry == null) {
            return new ValidationResult(mapping.mappingId(), mapping.version(), issues);
        }
        if (mapping.status() == DesignCodeComponentMapping.Status.SUPERSEDED) {
            issues.add(error("COMPONENT_MAPPING_SUPERSEDED",
                    "SUPERSEDED Mapping은 승인 또는 생성에 사용할 수 없습니다.", mapping.mappingId()));
        }
        if (rendererProfile == null || rendererProfile.isBlank()
                || !mapping.supportedRendererProfiles().contains(rendererProfile)) {
            issues.add(error("RENDERER_PROFILE_NOT_SUPPORTED",
                    "Mapping이 요청 Renderer Profile을 지원하지 않습니다.", rendererProfile));
        }
        if (!mapping.sourceRevision().equals(registry.sourceRevision())) {
            issues.add(error("MAPPING_REGISTRY_REVISION_MISMATCH",
                    "Mapping Source Revision과 Registry Source Revision이 다릅니다.", mapping.mappingId()));
        }

        ComponentCatalog.Entry catalogEntry = catalog.components().get(mapping.logicalType());
        if (catalogEntry == null) {
            issues.add(error("MAPPING_LOGICAL_TYPE_NOT_IN_CATALOG",
                    "Mapping logicalType이 Component Catalog에 없습니다.", mapping.logicalType()));
            return new ValidationResult(mapping.mappingId(), mapping.version(), issues);
        }
        if (!catalogEntry.atomicComponent()) {
            issues.add(error("MAPPING_LOGICAL_TYPE_NOT_COMPONENT",
                    "Code Component Mapping은 원자 Component logicalType만 참조할 수 있습니다.",
                    mapping.logicalType()));
        }

        ComponentRegistrySnapshotV3.Binding binding = registry.bindings().get(mapping.logicalType());
        if (binding == null) {
            issues.add(error("MAPPING_REGISTRY_BINDING_MISSING",
                    "Mapping logicalType의 Published Registry Binding이 없습니다.", mapping.logicalType()));
        } else {
            if (!binding.currentForGeneration()) {
                issues.add(error("MAPPING_REGISTRY_BINDING_NOT_CURRENT",
                        "Mapping이 참조한 Registry Binding은 현재 생성에 사용할 수 없습니다.",
                        mapping.logicalType()));
            }
            if (!mapping.figmaComponentSetKey().equals(binding.componentSetKey())) {
                issues.add(error("MAPPING_FIGMA_KEY_MISMATCH",
                        "Mapping의 Figma Component Set Key가 승인 Registry Binding과 다릅니다.",
                        mapping.logicalType()));
            }
        }

        validateProperties(catalogEntry, mapping, issues);
        return new ValidationResult(mapping.mappingId(), mapping.version(), issues);
    }

    public ValidationResult requireValid(
            ComponentCatalog catalog,
            String catalogHash,
            ComponentRegistrySnapshotV3 registry,
            DesignCodeComponentMapping mapping,
            String rendererProfile) {
        ValidationResult result = validate(catalog, catalogHash, registry, mapping, rendererProfile);
        if (!result.valid()) throw new MappingCrossValidationException(result);
        return result;
    }

    private void validateProperties(
            ComponentCatalog.Entry catalogEntry,
            DesignCodeComponentMapping mapping,
            List<DesignSystemIssue> issues) {
        Map<String, ComponentCatalog.Property> catalogByFigmaProperty = new HashMap<>();
        catalogEntry.properties().values().forEach(property -> {
            if (property.figmaProperty() != null && !property.figmaProperty().isBlank()) {
                catalogByFigmaProperty.put(property.figmaProperty(), property);
            }
        });
        Map<String, DesignCodeComponentMapping.PropertyMapping> mappingByFigmaProperty = new HashMap<>();
        mapping.propertyMappings().forEach(property ->
                mappingByFigmaProperty.put(property.figmaProperty(), property));

        for (DesignCodeComponentMapping.PropertyMapping property : mapping.propertyMappings()) {
            ComponentCatalog.Property catalogProperty = catalogByFigmaProperty.get(property.figmaProperty());
            if (catalogProperty == null) {
                issues.add(error("MAPPING_PROPERTY_NOT_IN_CATALOG",
                        "Mapping의 Figma Property가 Catalog Component 계약에 없습니다.",
                        property.figmaProperty()));
                continue;
            }
            validateVariantValues(catalogProperty, property, issues);
        }

        for (String requiredPropertyId : catalogEntry.requiredProperties()) {
            ComponentCatalog.Property required = catalogEntry.properties().get(requiredPropertyId);
            if (required != null && !mappingByFigmaProperty.containsKey(required.figmaProperty())) {
                issues.add(error("REQUIRED_CATALOG_PROPERTY_NOT_MAPPED",
                        "Catalog 필수 Property가 Fragment Parameter에 연결되지 않았습니다.",
                        required.figmaProperty()));
            }
        }
    }

    private void validateVariantValues(
            ComponentCatalog.Property catalogProperty,
            DesignCodeComponentMapping.PropertyMapping mappingProperty,
            List<DesignSystemIssue> issues) {
        if (catalogProperty.type() != ComponentRegistryEntry.PropertyType.VARIANT
                || catalogProperty.values().isEmpty()) {
            return;
        }
        Set<String> allowedFigmaValues = new LinkedHashSet<>(catalogProperty.values().values());
        for (String mappedValue : mappingProperty.valueMapping().keySet()) {
            if (!allowedFigmaValues.contains(mappedValue)) {
                issues.add(error("MAPPING_VARIANT_VALUE_NOT_IN_CATALOG",
                        "Mapping Variant 값이 Catalog 허용 Figma 값에 없습니다.",
                        mappingProperty.figmaProperty() + "=" + mappedValue));
            }
        }
        if (mappingProperty.fallbackValue() == null) {
            allowedFigmaValues.stream()
                    .filter(value -> !mappingProperty.valueMapping().containsKey(value))
                    .forEach(value -> issues.add(error("CATALOG_VARIANT_VALUE_NOT_MAPPED",
                            "Catalog 허용 Variant에 Mapping 또는 명시적 Fallback이 없습니다.",
                            mappingProperty.figmaProperty() + "=" + value)));
        }
    }

    private DesignSystemIssue error(String code, String message, String target) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, target);
    }

    public record ValidationResult(String mappingId, String mappingVersion, List<DesignSystemIssue> issues) {
        public ValidationResult {
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                    || issue.severity() == DesignSystemIssue.Severity.FATAL);
        }
    }

    public static final class MappingCrossValidationException extends IllegalStateException {
        private final ValidationResult result;

        public MappingCrossValidationException(ValidationResult result) {
            super("Catalog·Registry·Mapping 교차 검증에 실패했습니다: "
                    + result.mappingId() + "@" + result.mappingVersion());
            this.result = result;
        }

        public ValidationResult result() {
            return result;
        }
    }
}
