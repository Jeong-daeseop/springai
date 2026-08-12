package com.krdevops.springai.model.designsystem;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.krdevops.springai.model.design.role.Platform;
import com.krdevops.springai.model.design.role.SemanticRole;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** ComponentRegistry.components의 값. 논리 타입 하나(맵 키)가 Published Figma Component Set에서 어떻게 표현되는지 기록한다. */
public record ComponentRegistryEntry(
        String componentSetKey,
        String componentName,
        PublishStatus publishStatus,
        LifecycleStatus lifecycleStatus,
        String replacementLogicalType,
        List<String> aliases,
        Map<String, String> variants,
        Map<String, PropertyMapping> properties,
        Set<SemanticRole> roles,
        Set<Platform> supportedPlatforms,
        Map<String, VariantAxisDefinition> variantAxes,
        Set<String> requiredProperties,
        String codeComponent,
        String documentationUrl,
        String contractVersion
) {
    public ComponentRegistryEntry {
        if (componentSetKey == null || componentSetKey.isBlank()) {
            throw new IllegalArgumentException("componentSetKey는 필수입니다.");
        }
        publishStatus = publishStatus == null ? PublishStatus.UNPUBLISHED : publishStatus;
        lifecycleStatus = lifecycleStatus == null ? LifecycleStatus.ACTIVE : lifecycleStatus;
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        variants = variants == null ? Map.of() : Map.copyOf(variants);
        properties = properties == null ? Map.of() : Map.copyOf(properties);
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        supportedPlatforms = supportedPlatforms == null ? Set.of() : Set.copyOf(supportedPlatforms);
        variantAxes = variantAxes == null ? Map.of() : Map.copyOf(variantAxes);
        requiredProperties = requiredProperties == null ? Set.of() : Set.copyOf(requiredProperties);
        contractVersion = contractVersion == null || contractVersion.isBlank() ? "1.0.0" : contractVersion;
    }

    /** lifecycle 메타데이터 도입 전 R4 호출자 호환. */
    public ComponentRegistryEntry(
            String componentSetKey,
            String componentName,
            PublishStatus publishStatus,
            Map<String, String> variants,
            Map<String, PropertyMapping> properties) {
        this(componentSetKey, componentName, publishStatus, LifecycleStatus.ACTIVE,
                null, List.of(), variants, properties, Set.of(), Set.of(), Map.of(), Set.of(),
                null, null, "1.0.0");
    }

    /** Role·Variant Contract v2 도입 전 lifecycle 호출자 호환. */
    public ComponentRegistryEntry(
            String componentSetKey,
            String componentName,
            PublishStatus publishStatus,
            LifecycleStatus lifecycleStatus,
            String replacementLogicalType,
            List<String> aliases,
            Map<String, String> variants,
            Map<String, PropertyMapping> properties) {
        this(componentSetKey, componentName, publishStatus, lifecycleStatus, replacementLogicalType,
                aliases, variants, properties, Set.of(), Set.of(), Map.of(), Set.of(),
                null, null, "1.0.0");
    }

    /** R1 호환 생성자. Publish 메타데이터가 없던 기존 Registry를 UNPUBLISHED로 해석한다. */
    public ComponentRegistryEntry(String componentSetKey, Map<String, PropertyMapping> properties) {
        this(componentSetKey, null, PublishStatus.UNPUBLISHED, LifecycleStatus.ACTIVE,
                null, List.of(), Map.of(), properties, Set.of(), Set.of(), Map.of(), Set.of(),
                null, null, "1.0.0");
    }

    public record PropertyMapping(String figmaProperty, PropertyType type, Map<String, String> values) {
        public PropertyMapping {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public enum PropertyType { TEXT, BOOLEAN, VARIANT, INSTANCE_SWAP }

    public enum PublishStatus { UNPUBLISHED, CURRENT, CHANGED }

    public enum LifecycleStatus { DRAFT, ACTIVE, CURRENT, DEPRECATED, REMOVED }

    @JsonIgnore
    public boolean currentForGeneration() {
        return publishStatus == PublishStatus.CURRENT
                && (lifecycleStatus == LifecycleStatus.CURRENT || lifecycleStatus == LifecycleStatus.ACTIVE);
    }
}
