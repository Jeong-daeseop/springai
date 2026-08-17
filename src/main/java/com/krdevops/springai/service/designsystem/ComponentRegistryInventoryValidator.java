package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Registry v3 Published Key와 실제 Figma Library Inventory의 교차 검증기. */
@Service
public class ComponentRegistryInventoryValidator {
    public List<DesignSystemIssue> validate(ComponentRegistrySnapshotV3 registry,
                                             FigmaLibraryInventorySnapshot inventory) {
        return validate(registry, inventory, null);
    }

    public List<DesignSystemIssue> validate(ComponentRegistrySnapshotV3 registry,
                                             FigmaLibraryInventorySnapshot inventory,
                                             ComponentCatalog catalog) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (registry == null || inventory == null) {
            return List.of(new DesignSystemIssue("FIGMA_INVENTORY_MISSING", DesignSystemIssue.Severity.ERROR,
                    "Registry와 Figma Inventory Snapshot은 모두 필요합니다.", null));
        }
        if (!registry.profileId().equals(inventory.profileId())
                || !registry.registryVersion().equals(inventory.registryVersion())) {
            issues.add(new DesignSystemIssue("FIGMA_INVENTORY_VERSION_MISMATCH", DesignSystemIssue.Severity.ERROR,
                    "Registry와 Inventory의 Profile/Registry 버전이 다릅니다.", registry.registryVersion()));
            return List.copyOf(issues);
        }
        registry.bindings().forEach((logicalType, binding) -> {
            var actual = inventory.components().values().stream()
                    .filter(component -> binding.componentSetKey().equals(component.componentSetKey()))
                    .findFirst().orElse(null);
            if (actual == null) {
                issues.add(new DesignSystemIssue("PUBLISHED_COMPONENT_NOT_IN_INVENTORY",
                        DesignSystemIssue.Severity.ERROR, "Published Component Set이 Inventory에 없습니다.", logicalType));
                return;
            }
            binding.variants().forEach((variant, key) -> {
                if (!actual.variants().containsKey(variant)
                        || !key.equals(actual.variants().get(variant))) {
                    issues.add(new DesignSystemIssue("PUBLISHED_VARIANT_NOT_IN_INVENTORY",
                            DesignSystemIssue.Severity.ERROR, "Published Variant Key가 Inventory와 다릅니다.",
                            logicalType + "/" + variant));
                }
            });
            if (catalog != null) {
                ComponentCatalog.Entry contract = catalog.components().get(logicalType);
                if (contract != null) {
                    contract.properties().forEach((logicalName, property) -> {
                        var actualProperty = actual.properties().get(property.figmaProperty());
                        if (actualProperty == null || !property.type().name().equalsIgnoreCase(actualProperty.type())) {
                            issues.add(new DesignSystemIssue("PUBLISHED_PROPERTY_NOT_IN_INVENTORY",
                                    DesignSystemIssue.Severity.ERROR, "Published Property가 Inventory와 다릅니다.",
                                    logicalType + "/" + logicalName));
                        } else if (!property.values().isEmpty()
                                && !actualProperty.values().containsAll(property.values().values())) {
                            issues.add(new DesignSystemIssue("PUBLISHED_PROPERTY_VALUE_NOT_IN_INVENTORY",
                                    DesignSystemIssue.Severity.ERROR, "허용 Property 값이 Inventory와 다릅니다.",
                                    logicalType + "/" + logicalName));
                        }
                    });
                    contract.requiredProperties().forEach(required -> {
                        ComponentCatalog.Property property = contract.properties().get(required);
                        String figmaName = property == null ? required : property.figmaProperty();
                        if (!actual.properties().containsKey(figmaName)) {
                            issues.add(new DesignSystemIssue("REQUIRED_PROPERTY_NOT_IN_INVENTORY",
                                    DesignSystemIssue.Severity.ERROR, "필수 Property가 Inventory에 없습니다.",
                                    logicalType + "/" + required));
                        }
                    });
                }
            }
        });
        return List.copyOf(issues);
    }
}
