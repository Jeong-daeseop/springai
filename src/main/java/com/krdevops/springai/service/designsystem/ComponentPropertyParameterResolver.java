package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 승인 Mapping에 따라 Figma Property를 Fragment Parameter 입력으로 투영한다. */
@Service
public class ComponentPropertyParameterResolver {

    public Resolution resolve(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaProperties) {
        return resolve(mapping, figmaProperties, true);
    }

    /** 승인 전 Fixture Preview에서 동일 변환 규칙을 쓰되 Mapping 상태 Gate만 유예한다. */
    public Resolution resolveCandidate(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaProperties) {
        return resolve(mapping, figmaProperties, false);
    }

    private Resolution resolve(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaProperties,
            boolean requireApproved) {
        if (mapping == null) throw new IllegalArgumentException("mapping은 필수입니다.");
        Map<String, ?> input = figmaProperties == null ? Map.of() : figmaProperties;
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        LinkedHashSet<String> consumed = new LinkedHashSet<>();
        List<ResolutionIssue> issues = new ArrayList<>();

        if (requireApproved && mapping.status() != DesignCodeComponentMapping.Status.APPROVED) {
            issues.add(new ResolutionIssue("MAPPING_NOT_APPROVED", Severity.ERROR,
                    "APPROVED Component Mapping만 Fragment Parameter 해석에 사용할 수 있습니다.",
                    mapping.mappingId()));
        }

        for (DesignCodeComponentMapping.PropertyMapping property : mapping.propertyMappings()) {
            boolean supplied = input.containsKey(property.figmaProperty())
                    && input.get(property.figmaProperty()) != null;
            Object value = supplied ? input.get(property.figmaProperty()) : property.defaultValue();
            if (supplied) {
                consumed.add(property.figmaProperty());
            } else if (value != null) {
                issues.add(new ResolutionIssue("DEFAULT_VALUE_APPLIED", Severity.INFO,
                        "누락된 Figma Property에 Mapping 기본값을 적용했습니다.",
                        property.figmaProperty()));
            }

            if (value == null) {
                if (property.required()) {
                    issues.add(new ResolutionIssue("REQUIRED_PROPERTY_MISSING", Severity.ERROR,
                            "필수 Figma Property가 없고 기본값도 없습니다.",
                            property.figmaProperty()));
                }
                continue;
            }
            parameters.put(property.fragmentParameter(), value);
        }

        LinkedHashSet<String> unmapped = new LinkedHashSet<>(input.keySet());
        unmapped.removeAll(consumed);
        for (String property : unmapped) {
            issues.add(new ResolutionIssue("UNMAPPED_FIGMA_PROPERTY", Severity.WARNING,
                    "Fragment Parameter에 연결되지 않은 Figma Property입니다.", property));
        }

        return new Resolution(mapping.mappingId(), mapping.version(), mapping.thymeleafFragment(),
                parameters, consumed, unmapped, issues);
    }

    /** Apply 경계에서 오류가 있는 부분 해석 결과를 fail-closed로 차단한다. */
    public Resolution requireResolved(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaProperties) {
        Resolution resolution = resolve(mapping, figmaProperties);
        if (!resolution.valid()) {
            throw new ComponentPropertyResolutionException(resolution);
        }
        return resolution;
    }

    public record Resolution(
            String mappingId,
            String mappingVersion,
            String thymeleafFragment,
            Map<String, Object> fragmentParameters,
            Set<String> consumedFigmaProperties,
            Set<String> unmappedFigmaProperties,
            List<ResolutionIssue> issues
    ) {
        public Resolution {
            fragmentParameters = Collections.unmodifiableMap(new LinkedHashMap<>(fragmentParameters));
            consumedFigmaProperties = Collections.unmodifiableSet(
                    new LinkedHashSet<>(consumedFigmaProperties));
            unmappedFigmaProperties = Collections.unmodifiableSet(
                    new LinkedHashSet<>(unmappedFigmaProperties));
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public record ResolutionIssue(String code, Severity severity, String message, String target) {
        public ResolutionIssue {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("issue code는 필수입니다.");
            if (severity == null) throw new IllegalArgumentException("issue severity는 필수입니다.");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("issue message는 필수입니다.");
        }
    }

    public enum Severity { INFO, WARNING, ERROR }

    public static final class ComponentPropertyResolutionException extends IllegalStateException {
        private final Resolution resolution;

        public ComponentPropertyResolutionException(Resolution resolution) {
            super("Component Property를 Fragment Parameter로 완전히 해석할 수 없습니다: "
                    + resolution.mappingId() + "@" + resolution.mappingVersion());
            this.resolution = resolution;
        }

        public Resolution resolution() {
            return resolution;
        }
    }
}
