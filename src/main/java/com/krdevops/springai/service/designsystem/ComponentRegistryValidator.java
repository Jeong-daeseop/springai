package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

/** ComponentRegistry의 구조 검증과 R4 Published Registry 사전 검증을 담당한다. */
@Service
public class ComponentRegistryValidator {

    private final ComponentContractValidator contractValidator = new ComponentContractValidator();

    public List<DesignSystemIssue> validate(ComponentRegistry registry) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (registry == null) {
            issues.add(fatal("REGISTRY_NULL", "ComponentRegistry가 null입니다.", null));
            return issues;
        }

        Map<String, String> publishedKeyToLogicalType = new HashMap<>();
        Map<String, String> aliasOwners = new HashMap<>();
        for (var entry : registry.components().entrySet()) {
            String logicalType = entry.getKey();
            ComponentRegistryEntry value = entry.getValue();
            detectDuplicateKey(value.componentSetKey(), logicalType, publishedKeyToLogicalType, issues);
            for (var variant : value.variants().entrySet()) {
                detectDuplicateKey(variant.getValue(), logicalType + "/" + variant.getKey(),
                        publishedKeyToLogicalType, issues);
            }
            validatePropertyMappings(logicalType, value, issues);
            validateLifecycle(logicalType, value, registry, aliasOwners, issues);
            if (!value.roles().isEmpty() || !value.variantAxes().isEmpty()) {
                issues.addAll(contractValidator.validate(logicalType, value));
            }
        }
        detectReplacementCycles(registry, issues);
        for (var entry : registry.variables().entrySet()) {
            detectDuplicateKey(entry.getValue().variableKey(), entry.getKey(), publishedKeyToLogicalType, issues);
        }
        return issues;
    }

    /**
     * KRV-021: 신규(=이전 스냅샷에 없던) Component는 publishStatus/lifecycleStatus가
     * 모두 CURRENT인 조합만 허용한다. 기존에 이미 등록된 항목의 DEPRECATED 전환 등은
     * {@link #validate(ComponentRegistry)}의 {@code validateLifecycle}이 별도로 검증한다.
     */
    public List<DesignSystemIssue> validateNewEntryLifecycle(ComponentRegistry candidate, ComponentRegistry previous) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (candidate == null) {
            return issues;
        }
        Set<String> previousLogicalTypes = previous == null ? Set.of() : previous.components().keySet();
        candidate.components().forEach((logicalType, entry) -> {
            if (previousLogicalTypes.contains(logicalType)) {
                return;
            }
            if (!entry.currentForGeneration()) {
                issues.add(error("NEW_COMPONENT_LIFECYCLE_INVALID",
                        logicalType + "는 신규 등록 시 publishStatus=CURRENT, lifecycleStatus=CURRENT 조합만 허용됩니다"
                                + " (현재 publishStatus=" + entry.publishStatus()
                                + ", lifecycleStatus=" + entry.lifecycleStatus() + ").",
                        logicalType));
            }
        });
        return issues;
    }

    /** Publish Sync에서는 공개 Key가 실제 Library의 CURRENT 자산에서 왔는지까지 강제한다. */
    public List<DesignSystemIssue> validatePublished(ComponentRegistry registry) {
        List<DesignSystemIssue> issues = new ArrayList<>(validate(registry));
        if (registry == null) {
            return issues;
        }
        if (registry.library() == null || registry.library().fileKey() == null
                || registry.library().fileKey().isBlank()) {
            issues.add(error("LIBRARY_FILE_KEY_MISSING", "Published Library fileKey가 필요합니다.", registry.profileId()));
        }
        registry.components().forEach((logicalType, entry) -> {
            if (entry.publishStatus() != ComponentRegistryEntry.PublishStatus.CURRENT) {
                issues.add(error("COMPONENT_NOT_CURRENT",
                        logicalType + "의 Publish 상태가 CURRENT가 아닙니다: " + entry.publishStatus(), logicalType));
            }
            if (entry.variants().isEmpty()) {
                issues.add(warning("COMPONENT_VARIANT_KEYS_EMPTY",
                        logicalType + "의 Published Variant Component Key가 비어 있습니다.", logicalType));
            }
        });
        registry.variables().forEach((logicalId, entry) -> {
            if (entry.publishStatus() != ComponentRegistryEntry.PublishStatus.CURRENT) {
                issues.add(error("VARIABLE_NOT_CURRENT",
                        logicalId + "의 Publish 상태가 CURRENT가 아닙니다: " + entry.publishStatus(), logicalId));
            }
            if (entry.collectionKey() == null || entry.collectionKey().isBlank()) {
                issues.add(error("VARIABLE_COLLECTION_KEY_MISSING",
                        logicalId + "의 Published Variable Collection Key가 없습니다.", logicalId));
            }
        });
        return issues;
    }

    private void validateLifecycle(
            String logicalType,
            ComponentRegistryEntry entry,
            ComponentRegistry registry,
            Map<String, String> aliasOwners,
            List<DesignSystemIssue> issues
    ) {
        for (String alias : entry.aliases()) {
            String owner = aliasOwners.putIfAbsent(alias, logicalType);
            if (alias.equals(logicalType) || registry.components().containsKey(alias) || owner != null) {
                issues.add(error("COMPONENT_ALIAS_CONFLICT",
                        "alias " + alias + "가 컴포넌트 논리 ID와 충돌하거나 중복되었습니다.", logicalType));
            }
        }
        if (entry.lifecycleStatus() == ComponentRegistryEntry.LifecycleStatus.DEPRECATED) {
            String replacement = entry.replacementLogicalType();
            if (replacement == null || replacement.isBlank()
                    || replacement.equals(logicalType)
                    || !registry.components().containsKey(replacement)) {
                issues.add(error("INVALID_COMPONENT_REPLACEMENT",
                        logicalType + "의 대체 컴포넌트가 없거나 유효하지 않습니다.", logicalType));
            }
        }
    }

    private void detectReplacementCycles(ComponentRegistry registry, List<DesignSystemIssue> issues) {
        for (String logicalType : registry.components().keySet()) {
            Set<String> visited = new HashSet<>();
            String current = logicalType;
            while (current != null) {
                if (!visited.add(current)) {
                    issues.add(error("COMPONENT_REPLACEMENT_CYCLE",
                            logicalType + "의 대체 컴포넌트 경로에 순환 참조가 있습니다.", logicalType));
                    break;
                }
                ComponentRegistryEntry entry = registry.components().get(current);
                if (entry == null || entry.lifecycleStatus() != ComponentRegistryEntry.LifecycleStatus.DEPRECATED) {
                    break;
                }
                current = entry.replacementLogicalType();
            }
        }
    }

    private void detectDuplicateKey(
            String key,
            String logicalType,
            Map<String, String> keyOwners,
            List<DesignSystemIssue> issues
    ) {
        if (key == null || key.isBlank()) {
            issues.add(error("PUBLISHED_KEY_MISSING", logicalType + "의 Published Key가 없습니다.", logicalType));
            return;
        }
        String previousOwner = keyOwners.putIfAbsent(key, logicalType);
        if (previousOwner != null) {
            issues.add(error("DUPLICATE_COMPONENT_KEY",
                    "Published key " + key + "가 " + previousOwner + "와 " + logicalType + "에 중복 사용되었습니다.",
                    logicalType));
        }
    }

    private void validatePropertyMappings(String logicalType, ComponentRegistryEntry entry, List<DesignSystemIssue> issues) {
        entry.properties().forEach((propertyName, mapping) -> {
            boolean isVariant = mapping.type() == ComponentRegistryEntry.PropertyType.VARIANT;
            boolean hasValues = mapping.values() != null && !mapping.values().isEmpty();
            if (isVariant && !hasValues) {
                issues.add(warning("VARIANT_PROPERTY_WITHOUT_VALUES",
                        logicalType + "." + propertyName + "이(가) VARIANT 타입인데 values가 비어 있습니다.", logicalType));
            }
        });
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
