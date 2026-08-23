package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 기준 화면에서 사용된 Component의 Figma Property→Fragment Parameter 완전성을 검증한다. */
@Service
public class ComponentMappingCoverageValidator {

    public CoverageResult validate(
            ComponentRegistry registry,
            Map<String, String> requiredComponents,
            List<DesignCodeComponentMapping> mappings,
            String rendererProfile) {
        if (registry == null) throw new IllegalArgumentException("registry는 필수입니다.");
        Map<String, String> required = requiredComponents == null ? Map.of() : requiredComponents;
        List<DesignCodeComponentMapping> candidates = mappings == null ? List.of() : mappings;
        List<CoverageIssue> issues = new ArrayList<>();
        int mappedProperties = 0;
        int totalProperties = 0;

        for (Map.Entry<String, String> requiredComponent : required.entrySet()) {
            String logicalType = requiredComponent.getKey();
            ComponentRegistryEntry entry = registry.components().get(logicalType);
            if (entry == null) {
                issues.add(issue("REGISTRY_COMPONENT_MISSING", logicalType,
                        "기준 화면 Component가 Registry에 없습니다."));
                continue;
            }
            if (!entry.componentSetKey().equals(requiredComponent.getValue())) {
                issues.add(issue("COMPONENT_SET_KEY_MISMATCH", logicalType,
                        "기준 화면과 Registry의 Published Component Set Key가 다릅니다."));
                continue;
            }
            DesignCodeComponentMapping mapping = candidates.stream()
                    .filter(value -> value.status() == DesignCodeComponentMapping.Status.APPROVED)
                    .filter(value -> logicalType.equals(value.logicalType()))
                    .filter(value -> entry.componentSetKey().equals(value.figmaComponentSetKey()))
                    .filter(value -> value.supportedRendererProfiles().contains(rendererProfile))
                    .findFirst().orElse(null);
            if (mapping == null) {
                issues.add(issue("APPROVED_MAPPING_MISSING", logicalType,
                        "Renderer Profile을 지원하는 승인 Mapping이 없습니다."));
                totalProperties += uniqueFigmaPropertyCount(entry);
                continue;
            }

            Map<String, DesignCodeComponentMapping.PropertyMapping> byFigma = new LinkedHashMap<>();
            for (DesignCodeComponentMapping.PropertyMapping property : mapping.propertyMappings()) {
                byFigma.put(property.figmaProperty(), property);
            }
            Map<String, List<String>> logicalNamesByFigma = new LinkedHashMap<>();
            entry.properties().forEach((logicalName, property) -> logicalNamesByFigma
                    .computeIfAbsent(property.figmaProperty(), ignored -> new ArrayList<>())
                    .add(logicalName));
            totalProperties += logicalNamesByFigma.size();
            for (Map.Entry<String, List<String>> property : logicalNamesByFigma.entrySet()) {
                String figmaProperty = property.getKey();
                List<String> logicalProperties = property.getValue();
                DesignCodeComponentMapping.PropertyMapping mapped = byFigma.get(figmaProperty);
                if (mapped == null) {
                    issues.add(issue("FIGMA_PROPERTY_UNMAPPED", logicalType + "." + figmaProperty,
                            "Registry Figma Property에 대응하는 Fragment Parameter가 없습니다."));
                    continue;
                }
                mappedProperties++;
                if (!logicalProperties.contains(mapped.fragmentParameter())) {
                    issues.add(issue("FRAGMENT_PARAMETER_MISMATCH", logicalType + "." + figmaProperty,
                            "Fragment Parameter가 Registry의 논리 Property 이름과 다릅니다."));
                }
                if (logicalProperties.stream().anyMatch(entry.requiredProperties()::contains)
                        && !mapped.required()) {
                    issues.add(issue("REQUIRED_PROPERTY_NOT_ENFORCED", logicalType + "." + figmaProperty,
                            "Registry 필수 Property가 Mapping에서 선택 항목으로 완화됐습니다."));
                }
            }
            Set<String> registryFigmaProperties = new HashSet<>();
            entry.properties().values().forEach(value -> registryFigmaProperties.add(value.figmaProperty()));
            byFigma.keySet().stream().filter(value -> !registryFigmaProperties.contains(value))
                    .forEach(value -> issues.add(issue("UNKNOWN_FIGMA_PROPERTY", logicalType + "." + value,
                            "Registry에 없는 Figma Property를 Mapping이 참조합니다.")));
        }
        return new CoverageResult(required.size(), totalProperties, mappedProperties, issues);
    }

    private CoverageIssue issue(String code, String target, String message) {
        return new CoverageIssue(code, target, message);
    }

    private int uniqueFigmaPropertyCount(ComponentRegistryEntry entry) {
        return (int) entry.properties().values().stream()
                .map(ComponentRegistryEntry.PropertyMapping::figmaProperty).distinct().count();
    }

    public record CoverageResult(
            int requiredComponentCount,
            int totalPropertyCount,
            int mappedPropertyCount,
            List<CoverageIssue> issues
    ) {
        public CoverageResult {
            issues = List.copyOf(issues);
        }

        public boolean complete() {
            return mappedPropertyCount == totalPropertyCount && issues.isEmpty();
        }

        public double coveragePercent() {
            return totalPropertyCount == 0 ? 100.0
                    : mappedPropertyCount * 100.0 / totalPropertyCount;
        }
    }

    public record CoverageIssue(String code, String target, String message) {}
}
