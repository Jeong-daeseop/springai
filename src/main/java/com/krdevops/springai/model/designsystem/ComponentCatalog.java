package com.krdevops.springai.model.designsystem;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Component 논리 계약의 단일 진실 공급원인 component-catalog-v2 모델. */
public record ComponentCatalog(
        String schemaVersion,
        String contractVersion,
        Map<String, Entry> components,
        FallbackPolicy fallbackPolicy
) {
    public static final String SCHEMA_VERSION = "component-catalog-v2";

    public ComponentCatalog {
        components = components == null ? Map.of() : Map.copyOf(components);
    }

    public record Entry(
            Kind kind,
            Requirement requirement,
            List<String> aliases,
            String replacementLogicalType,
            Map<String, Property> properties,
            List<String> composition,
            Set<String> roles,
            Set<Platform> supportedPlatforms,
            Set<String> requiredProperties,
            String codeComponent,
            String documentationUrl
    ) {
        public Entry {
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
            properties = properties == null ? Map.of() : Map.copyOf(properties);
            composition = composition == null ? List.of() : List.copyOf(composition);
            roles = roles == null ? Set.of() : Set.copyOf(roles);
            supportedPlatforms = supportedPlatforms == null ? Set.of() : Set.copyOf(supportedPlatforms);
            requiredProperties = requiredProperties == null ? Set.of() : Set.copyOf(requiredProperties);
        }

        public boolean atomicComponent() {
            return kind == Kind.COMPONENT && composition.isEmpty();
        }
    }

    public record Property(
            ComponentRegistryEntry.PropertyType type,
            String figmaProperty,
            String codeProperty,
            Map<String, String> values
    ) {
        public Property {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public record FallbackPolicy(
            String required,
            String optional,
            String unsupportedProperty,
            String deprecated
    ) {}

    public enum Kind { COMPONENT, PATTERN, PAGE_TEMPLATE }
    public enum Requirement { REQUIRED, OPTIONAL }
    public enum Platform { DESKTOP, TABLET, MOBILE }
}
