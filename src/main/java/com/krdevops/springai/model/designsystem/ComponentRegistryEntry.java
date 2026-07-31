package com.krdevops.springai.model.designsystem;

import java.util.List;
import java.util.Map;

/** ComponentRegistry.components의 값. 논리 타입 하나(맵 키)가 Published Figma Component Set에서 어떻게 표현되는지 기록한다. */
public record ComponentRegistryEntry(
        String componentSetKey,
        String componentName,
        PublishStatus publishStatus,
        LifecycleStatus lifecycleStatus,
        String replacementLogicalType,
        List<String> aliases,
        Map<String, String> variants,
        Map<String, PropertyMapping> properties
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
    }

    /** lifecycle 메타데이터 도입 전 R4 호출자 호환. */
    public ComponentRegistryEntry(
            String componentSetKey,
            String componentName,
            PublishStatus publishStatus,
            Map<String, String> variants,
            Map<String, PropertyMapping> properties) {
        this(componentSetKey, componentName, publishStatus, LifecycleStatus.ACTIVE,
                null, List.of(), variants, properties);
    }

    /** R1 호환 생성자. Publish 메타데이터가 없던 기존 Registry를 UNPUBLISHED로 해석한다. */
    public ComponentRegistryEntry(String componentSetKey, Map<String, PropertyMapping> properties) {
        this(componentSetKey, null, PublishStatus.UNPUBLISHED, LifecycleStatus.ACTIVE,
                null, List.of(), Map.of(), properties);
    }

    public record PropertyMapping(String figmaProperty, PropertyType type, Map<String, String> values) {
        public PropertyMapping {
            values = values == null ? Map.of() : Map.copyOf(values);
        }
    }

    public enum PropertyType { TEXT, BOOLEAN, VARIANT, INSTANCE_SWAP }

    public enum PublishStatus { UNPUBLISHED, CURRENT, CHANGED }

    public enum LifecycleStatus { ACTIVE, DEPRECATED }
}
