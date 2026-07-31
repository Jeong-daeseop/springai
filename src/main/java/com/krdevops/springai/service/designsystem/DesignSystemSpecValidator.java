package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.DesignSystemSpec;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * R1-030: DesignSystemSpec의 Java 의미 검증. JSON Schema 구조 검증(R1-033, P1)과는 별개로
 * 참조 무결성·중복 ID처럼 스키마만으로는 걸러지지 않는 문제를 찾는다.
 */
@Service
public class DesignSystemSpecValidator {

    public List<DesignSystemIssue> validate(DesignSystemSpec spec) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (spec == null) {
            issues.add(fatal("SPEC_NULL", "DesignSystemSpec이 null입니다.", null));
            return issues;
        }

        Set<String> componentIds = new HashSet<>();
        for (DesignSystemSpec.ComponentDefinition component : spec.components()) {
            if (!componentIds.add(component.id())) {
                issues.add(error("DUPLICATE_COMPONENT_ID",
                        "컴포넌트 id가 중복되었습니다: " + component.id(), component.id()));
            }
            validateVariantConsistency(component, issues);
            validateMetadataAndLayout(component, issues);
        }
        validateComponentLifecycle(spec, componentIds, issues);

        Set<String> patternIds = new HashSet<>();
        for (DesignSystemSpec.PatternDefinition pattern : spec.patterns()) {
            if (!patternIds.add(pattern.id())) {
                issues.add(error("DUPLICATE_PATTERN_ID",
                        "패턴 id가 중복되었습니다: " + pattern.id(), pattern.id()));
            }
            for (String composedId : pattern.composedOf()) {
                if (!componentIds.contains(composedId)) {
                    issues.add(error("PATTERN_UNKNOWN_COMPONENT",
                            "패턴 " + pattern.id() + "이(가) 존재하지 않는 컴포넌트를 참조합니다: " + composedId,
                            pattern.id()));
                }
            }
        }

        for (DesignSystemSpec.VariableCollection collection : spec.variableCollections()) {
            Set<String> declaredModes = new HashSet<>(collection.modes());
            for (var entry : collection.valuesByMode().entrySet()) {
                for (String mode : entry.getValue().keySet()) {
                    if (!declaredModes.contains(mode)) {
                        issues.add(warning("VARIABLE_UNDECLARED_MODE",
                                "Variable Collection " + collection.name() + "의 변수 " + entry.getKey()
                                        + "가 선언되지 않은 모드 " + mode + "를 사용합니다.", collection.name()));
                    }
                }
            }
        }

        return issues;
    }

    private void validateComponentLifecycle(
            DesignSystemSpec spec,
            Set<String> componentIds,
            List<DesignSystemIssue> issues
    ) {
        java.util.Map<String, String> aliasOwners = new java.util.HashMap<>();
        for (var component : spec.components()) {
            for (String alias : component.aliases()) {
                String owner = aliasOwners.putIfAbsent(alias, component.id());
                if (alias.equals(component.id()) || componentIds.contains(alias) || owner != null) {
                    issues.add(error("COMPONENT_ALIAS_CONFLICT",
                            "컴포넌트 alias가 논리 ID와 충돌하거나 중복되었습니다: " + alias, component.id()));
                }
            }
            if (component.lifecycleStatus() == DesignSystemSpec.ComponentDefinition.LifecycleStatus.DEPRECATED) {
                String replacement = component.replacementLogicalType();
                if (replacement == null || replacement.isBlank()
                        || replacement.equals(component.id()) || !componentIds.contains(replacement)) {
                    issues.add(error("INVALID_COMPONENT_REPLACEMENT",
                            "폐기된 컴포넌트 " + component.id() + "의 대체 컴포넌트가 유효하지 않습니다.",
                            component.id()));
                }
            }
        }
    }

    private void validateVariantConsistency(
            DesignSystemSpec.ComponentDefinition component, List<DesignSystemIssue> issues) {
        for (DesignSystemSpec.ComponentDefinition.Property property : component.properties()) {
            boolean declaresVariant = property.type() == DesignSystemSpec.ComponentDefinition.PropertyType.VARIANT;
            boolean hasVariantEntry = component.variants().containsKey(property.name());
            if (declaresVariant && !hasVariantEntry) {
                issues.add(error("VARIANT_PROPERTY_WITHOUT_OPTIONS",
                        "컴포넌트 " + component.id() + "의 속성 " + property.name()
                                + "이(가) VARIANT 타입인데 variants에 옵션이 없습니다.", component.id()));
            }
        }
    }

    private void validateMetadataAndLayout(
            DesignSystemSpec.ComponentDefinition component,
            List<DesignSystemIssue> issues) {
        var developer = component.developer();
        if (developer != null && developer.documentationUrl() != null
                && !developer.documentationUrl().isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(developer.documentationUrl());
                if (!uri.isAbsolute()) {
                    throw new IllegalArgumentException("absolute URI required");
                }
            } catch (IllegalArgumentException exception) {
                issues.add(error("INVALID_DOCUMENTATION_URL",
                        "컴포넌트 " + component.id() + "의 documentationUrl이 올바르지 않습니다.",
                        component.id()));
            }
        }

        var layout = component.layout();
        if (layout == null) return;
        validateRange(component.id(), "width", layout.minWidth(), layout.maxWidth(), issues);
        validateRange(component.id(), "height", layout.minHeight(), layout.maxHeight(), issues);
    }

    private void validateRange(
            String componentId,
            String dimension,
            String minimum,
            String maximum,
            List<DesignSystemIssue> issues) {
        Double min = dimensionValue(minimum);
        Double max = dimensionValue(maximum);
        if ((minimum != null && min == null) || (maximum != null && max == null)) {
            issues.add(error("INVALID_LAYOUT_SIZE",
                    "컴포넌트 " + componentId + "의 " + dimension
                            + " min/max 값은 0보다 큰 숫자여야 합니다.", componentId));
            return;
        }
        if (min != null && max != null && min > max) {
            issues.add(error("INVALID_LAYOUT_RANGE",
                    "컴포넌트 " + componentId + "의 min" + dimension
                            + "가 max" + dimension + "보다 클 수 없습니다.", componentId));
        }
    }

    private Double dimensionValue(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            double value = Double.parseDouble(raw.replaceAll("[^0-9.-]", ""));
            return value > 0 ? value : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private DesignSystemIssue fatal(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.FATAL, message, targetId);
    }

    private DesignSystemIssue error(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, targetId);
    }

    private DesignSystemIssue warning(String code, String message, String targetId) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.WARNING, message, targetId);
    }
}
