package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Property→Parameter 결과의 Figma Variant/Boolean 값을 Fragment 계약 값으로 변환한다. */
@Service
public class ComponentVariantValueResolver {

    public Resolution resolve(
            DesignCodeComponentMapping mapping,
            ComponentPropertyParameterResolver.Resolution propertyResolution) {
        if (mapping == null) throw new IllegalArgumentException("mapping은 필수입니다.");
        if (propertyResolution == null) throw new IllegalArgumentException("propertyResolution은 필수입니다.");
        if (!mapping.mappingId().equals(propertyResolution.mappingId())
                || !mapping.version().equals(propertyResolution.mappingVersion())) {
            throw new IllegalArgumentException("Mapping과 Property Resolution의 ID·Version이 일치하지 않습니다.");
        }

        LinkedHashMap<String, Object> converted = new LinkedHashMap<>(
                propertyResolution.fragmentParameters());
        List<ConversionIssue> issues = new ArrayList<>();

        for (DesignCodeComponentMapping.PropertyMapping property : mapping.propertyMappings()) {
            if (property.valueMapping().isEmpty()
                    || !propertyResolution.consumedFigmaProperties().contains(property.figmaProperty())
                    || !converted.containsKey(property.fragmentParameter())) {
                continue;
            }
            Object rawValue = converted.get(property.fragmentParameter());
            String lookupKey = scalarKey(rawValue);
            if (lookupKey != null && property.valueMapping().containsKey(lookupKey)) {
                converted.put(property.fragmentParameter(), property.valueMapping().get(lookupKey));
                continue;
            }
            if (property.fallbackValue() != null) {
                converted.put(property.fragmentParameter(), property.fallbackValue());
                issues.add(new ConversionIssue("VARIANT_FALLBACK_APPLIED", Severity.WARNING,
                        "지원하지 않는 Figma Variant에 명시적 Fragment Fallback을 적용했습니다.",
                        property.figmaProperty(), rawValue));
                continue;
            }
            converted.remove(property.fragmentParameter());
            issues.add(new ConversionIssue("VARIANT_VALUE_UNSUPPORTED", Severity.ERROR,
                    "지원하지 않는 Figma Variant이며 명시적 Fallback이 없습니다.",
                    property.figmaProperty(), rawValue));
        }

        return new Resolution(propertyResolution, converted, issues);
    }

    public Resolution requireResolved(
            DesignCodeComponentMapping mapping,
            ComponentPropertyParameterResolver.Resolution propertyResolution) {
        Resolution resolution = resolve(mapping, propertyResolution);
        if (!resolution.valid()) throw new ComponentVariantResolutionException(resolution);
        return resolution;
    }

    private String scalarKey(Object value) {
        if (value instanceof CharSequence || value instanceof Boolean
                || value instanceof Number || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return null;
    }

    public record Resolution(
            ComponentPropertyParameterResolver.Resolution propertyResolution,
            Map<String, Object> fragmentParameters,
            List<ConversionIssue> issues
    ) {
        public Resolution {
            if (propertyResolution == null) {
                throw new IllegalArgumentException("propertyResolution은 필수입니다.");
            }
            fragmentParameters = Collections.unmodifiableMap(new LinkedHashMap<>(fragmentParameters));
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return propertyResolution.valid()
                    && issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public record ConversionIssue(
            String code,
            Severity severity,
            String message,
            String figmaProperty,
            Object rejectedValue
    ) {
        public ConversionIssue {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("issue code는 필수입니다.");
            if (severity == null) throw new IllegalArgumentException("issue severity는 필수입니다.");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("issue message는 필수입니다.");
            if (figmaProperty == null || figmaProperty.isBlank()) {
                throw new IllegalArgumentException("figmaProperty는 필수입니다.");
            }
        }
    }

    public enum Severity { WARNING, ERROR }

    public static final class ComponentVariantResolutionException extends IllegalStateException {
        private final Resolution resolution;

        public ComponentVariantResolutionException(Resolution resolution) {
            super("Figma Variant를 Fragment 값으로 완전히 변환할 수 없습니다: "
                    + resolution.propertyResolution().mappingId() + "@"
                    + resolution.propertyResolution().mappingVersion());
            this.resolution = resolution;
        }

        public Resolution resolution() {
            return resolution;
        }
    }
}
