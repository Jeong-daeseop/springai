package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Catalog SSOT와 Published Binding Snapshot을 교차 검증한다. */
@Service
public class ComponentRegistryBindingValidator {

    private final ComponentCatalogValidator catalogValidator;

    public ComponentRegistryBindingValidator(ComponentCatalogValidator catalogValidator) {
        this.catalogValidator = catalogValidator;
    }

    public List<DesignSystemIssue> validate(
            ComponentCatalog catalog, String catalogHash, ComponentRegistrySnapshotV3 registry) {
        return validate(catalog, catalogHash, registry, true);
    }

    public List<DesignSystemIssue> validate(
            ComponentCatalog catalog, String catalogHash, ComponentRegistrySnapshotV3 registry,
            boolean requireApproval) {
        List<DesignSystemIssue> issues = new ArrayList<>(catalogValidator.validate(catalog));
        if (registry == null) {
            issues.add(error("REGISTRY_NULL", "Component Registry Snapshot이 null입니다.", null));
            return issues;
        }
        if (!ComponentRegistrySnapshotV3.SCHEMA_VERSION.equals(registry.schemaVersion())) {
            issues.add(error("REGISTRY_SCHEMA_UNSUPPORTED", "지원하지 않는 Registry Schema입니다.", registry.schemaVersion()));
        }
        if (!catalog.contractVersion().equals(registry.catalogVersion())) {
            issues.add(error("CATALOG_REGISTRY_VERSION_MISMATCH", "Catalog와 Registry의 계약 버전이 다릅니다.", registry.registryVersion()));
        }
        if (catalogHash == null || !catalogHash.equals(registry.catalogHash())) {
            issues.add(error("CATALOG_HASH_MISMATCH", "Registry가 참조한 Catalog Hash가 현재 Catalog와 다릅니다.", registry.registryVersion()));
        }
        if (requireApproval && !registry.approved()) {
            issues.add(error("UNAPPROVED_REGISTRY", "사람 승인이 없는 Registry Snapshot입니다.", registry.registryVersion()));
        }

        Map<String, String> keyOwners = new HashMap<>();
        registry.bindings().forEach((logicalType, binding) -> {
            ComponentCatalog.Entry catalogEntry = catalog.components().get(logicalType);
            if (catalogEntry == null) {
                issues.add(error("UNKNOWN_LOGICAL_TYPE", "Catalog에 없는 Registry Binding입니다.", logicalType));
            } else {
                java.util.Set<String> declaredVariantNames = catalogEntry.properties().values().stream()
                        .filter(property -> property.type() == ComponentRegistryEntry.PropertyType.VARIANT)
                        .flatMap(property -> property.values().keySet().stream())
                        .collect(java.util.stream.Collectors.toSet());
                binding.variants().keySet().stream()
                        .filter(variant -> !declaredVariantNames.isEmpty() && !declaredVariantNames.contains(variant))
                        .forEach(variant -> issues.add(error("VARIANT_CONTRACT_MISMATCH",
                                "Registry Variant가 Catalog 허용값에 없습니다.", logicalType + "/" + variant)));
            }
            detectDuplicate(binding.componentSetKey(), logicalType, keyOwners, issues);
            binding.variants().forEach((variant, key) -> detectDuplicate(key, logicalType + "/" + variant, keyOwners, issues));
            if (!binding.currentForGeneration()) {
                issues.add(error("BINDING_NOT_CURRENT", "현재 생성에 사용할 수 없는 Binding입니다.", logicalType));
            }
        });

        catalog.components().forEach((logicalType, entry) -> {
            if (entry.atomicComponent() && entry.requirement() == ComponentCatalog.Requirement.REQUIRED
                    && !registry.bindings().containsKey(logicalType)) {
                issues.add(error("REQUIRED_BINDING_MISSING", "필수 Published Binding이 없습니다.", logicalType));
            }
        });
        return List.copyOf(issues);
    }

    private void detectDuplicate(String key, String owner, Map<String, String> owners,
            List<DesignSystemIssue> issues) {
        if (key == null || key.isBlank()) {
            issues.add(error("PUBLISHED_KEY_MISSING", "Published Key가 없습니다.", owner));
            return;
        }
        String previous = owners.putIfAbsent(key, owner);
        if (previous != null) {
            issues.add(error("PUBLISHED_KEY_DUPLICATED", "Published Key가 중복됐습니다: " + previous, owner));
        }
    }

    private DesignSystemIssue error(String code, String message, String target) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, target);
    }
}
