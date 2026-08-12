package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 자동 생성에 사용할 Component Contract v2의 완전성을 검증한다. */
@Component
public class ComponentContractValidator {

    public List<DesignSystemIssue> validate(String logicalType, ComponentRegistryEntry contract) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (contract == null) {
            return List.of(issue("COMPONENT_CONTRACT_MISSING", "Component Contract가 없습니다.", logicalType));
        }
        if (!contract.currentForGeneration()) {
            issues.add(issue("COMPONENT_NOT_CURRENT", "CURRENT Component만 생성에 사용할 수 있습니다.", logicalType));
        }
        if (contract.roles().isEmpty()) {
            issues.add(issue("COMPONENT_ROLE_MISSING", "지원 Semantic Role이 없습니다.", logicalType));
        }
        if (contract.supportedPlatforms().isEmpty()) {
            issues.add(issue("COMPONENT_PLATFORM_MISSING", "지원 Platform이 없습니다.", logicalType));
        }
        for (String required : contract.requiredProperties()) {
            if (!contract.properties().containsKey(required)) {
                issues.add(issue("REQUIRED_COMPONENT_PROPERTY_MISSING",
                        "필수 Property Mapping이 없습니다: " + required, logicalType));
            }
        }
        Set<String> figmaAxisNames = new HashSet<>();
        contract.variantAxes().forEach((logicalAxis, axis) -> {
            if (!logicalAxis.equals(axis.logicalName())) {
                issues.add(issue("VARIANT_AXIS_LOGICAL_NAME_MISMATCH",
                        logicalAxis + "과 " + axis.logicalName() + "이 일치하지 않습니다.", logicalType));
            }
            if (!figmaAxisNames.add(axis.figmaProperty())) {
                issues.add(issue("DUPLICATE_VARIANT_FIGMA_PROPERTY",
                        "Variant Figma Property가 중복되었습니다: " + axis.figmaProperty(), logicalType));
            }
            ComponentRegistryEntry.PropertyMapping mapping = contract.properties().get(logicalAxis);
            if (mapping == null || mapping.type() != ComponentRegistryEntry.PropertyType.VARIANT
                    || !axis.figmaProperty().equals(mapping.figmaProperty())) {
                issues.add(issue("VARIANT_PROPERTY_MAPPING_MISSING",
                        "Variant Axis에 대응하는 VARIANT Property Mapping이 없습니다: " + logicalAxis, logicalType));
            }
        });
        if (!contract.variantAxes().isEmpty() && contract.variants().isEmpty()) {
            issues.add(issue("PUBLISHED_VARIANT_KEYS_MISSING", "Published Variant Key가 없습니다.", logicalType));
        }
        return List.copyOf(issues);
    }

    private DesignSystemIssue issue(String code, String message, String target) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, target);
    }
}
