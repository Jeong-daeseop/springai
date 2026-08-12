package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry Contract와 Author Plugin이 수집한 실제 Figma 공개 Property를 비교한다. */
@Component
public class FigmaPropertyDriftValidator {

    public List<DesignSystemIssue> validate(
            String logicalType,
            ComponentRegistryEntry contract,
            LibraryComponentSnapshot actual
    ) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (actual == null || !contract.componentSetKey().equals(actual.componentSetKey())) {
            return List.of(issue("COMPONENT_PROPERTY_DRIFT", "Component Set Key가 일치하지 않습니다.", logicalType));
        }
        contract.properties().forEach((logicalName, mapping) -> {
            ActualProperty property = actual.properties().get(mapping.figmaProperty());
            if (property == null || !mapping.type().name().equalsIgnoreCase(property.type())) {
                issues.add(issue("COMPONENT_PROPERTY_DRIFT",
                        logicalName + "의 Figma Property 이름 또는 Type이 일치하지 않습니다.", logicalType));
            }
        });
        contract.variantAxes().forEach((logicalName, axis) -> {
            ActualProperty property = actual.properties().get(axis.figmaProperty());
            if (property == null || !property.values().containsAll(axis.allowedValues())) {
                issues.add(issue("COMPONENT_VARIANT_DRIFT",
                        logicalName + "의 실제 Variant 값이 Contract와 일치하지 않습니다.", logicalType));
            }
        });
        contract.requiredProperties().forEach(requiredName -> {
            ComponentRegistryEntry.PropertyMapping mapping = contract.properties().get(requiredName);
            String figmaProperty = mapping != null ? mapping.figmaProperty() : requiredName;
            if (!actual.properties().containsKey(figmaProperty)) {
                issues.add(issue("REQUIRED_COMPONENT_PROPERTY_MISSING",
                        "필수 Property가 실제 Figma Component에 없습니다: " + requiredName, logicalType));
            }
        });
        if (!actual.variants().isEmpty()) {
            contract.variants().keySet().forEach(variantName -> {
                if (!actual.variants().containsKey(variantName)) {
                    issues.add(issue("COMPONENT_VARIANT_NAME_DRIFT",
                            "Contract의 Variant 이름이 실제 Figma Component Set에 없습니다: " + variantName, logicalType));
                }
            });
            actual.variants().keySet().forEach(actualVariantName -> {
                if (!contract.variants().containsKey(actualVariantName)) {
                    issues.add(issue("COMPONENT_VARIANT_NAME_DRIFT",
                            "실제 Figma Component Set에 Contract가 모르는 Variant가 있습니다: " + actualVariantName,
                            logicalType));
                }
            });
        }
        actual.properties().forEach((actualName, ignored) -> {
            boolean declared = contract.properties().values().stream()
                    .anyMatch(mapping -> actualName.equals(mapping.figmaProperty()))
                    || contract.variantAxes().values().stream()
                    .anyMatch(axis -> actualName.equals(axis.figmaProperty()));
            if (!declared) {
                issues.add(issue("COMPONENT_UNDECLARED_PROPERTY_DRIFT",
                        "Contract에 없는 실제 Figma Property가 있습니다: " + actualName, logicalType));
            }
        });
        return List.copyOf(issues);
    }

    private DesignSystemIssue issue(String code, String message, String target) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.FATAL, message, target);
    }

    public record LibraryComponentSnapshot(
            String componentSetKey,
            Map<String, ActualProperty> properties,
            Map<String, String> variants
    ) {
        public LibraryComponentSnapshot {
            properties = properties == null ? Map.of() : Map.copyOf(properties);
            variants = variants == null ? Map.of() : Map.copyOf(variants);
        }
    }

    public record ActualProperty(String type, Set<String> values) {
        public ActualProperty {
            values = values == null ? Set.of() : Set.copyOf(values);
        }
    }
}
