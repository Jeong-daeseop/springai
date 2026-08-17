package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Catalog 내부의 alias, replacement, composition, 필수 속성 정합성을 검증한다. */
@Service
public class ComponentCatalogValidator {

    public List<DesignSystemIssue> validate(ComponentCatalog catalog) {
        List<DesignSystemIssue> issues = new ArrayList<>();
        if (catalog == null) {
            issues.add(error("CATALOG_NULL", "Component Catalog가 null입니다.", null));
            return issues;
        }
        if (!ComponentCatalog.SCHEMA_VERSION.equals(catalog.schemaVersion())) {
            issues.add(error("CATALOG_SCHEMA_UNSUPPORTED", "지원하지 않는 Catalog Schema입니다.", catalog.schemaVersion()));
        }

        Map<String, String> aliasOwners = new HashMap<>();
        catalog.components().forEach((logicalType, entry) -> {
            for (String alias : entry.aliases()) {
                String owner = aliasOwners.putIfAbsent(alias, logicalType);
                if (catalog.components().containsKey(alias) || owner != null) {
                    issues.add(error("CATALOG_ALIAS_CONFLICT", alias + " 별칭이 중복되거나 논리 타입과 충돌합니다.", logicalType));
                }
            }
            if (entry.replacementLogicalType() != null
                    && !catalog.components().containsKey(entry.replacementLogicalType())) {
                issues.add(error("CATALOG_REPLACEMENT_MISSING", "대체 논리 타입이 Catalog에 없습니다.", logicalType));
            }
            for (String target : entry.composition()) {
                if (!catalog.components().containsKey(target)) {
                    issues.add(error("COMPOSITION_TARGET_MISSING", "합성 대상이 Catalog에 없습니다: " + target, logicalType));
                }
            }
            for (String property : entry.requiredProperties()) {
                if (!entry.properties().containsKey(property)) {
                    issues.add(error("CATALOG_REQUIRED_PROPERTY_MISSING", "필수 속성 정의가 없습니다: " + property, logicalType));
                }
            }
        });
        detectCycles(catalog, false, issues);
        detectCycles(catalog, true, issues);
        return List.copyOf(issues);
    }

    private void detectCycles(ComponentCatalog catalog, boolean replacement, List<DesignSystemIssue> issues) {
        Set<String> finished = new HashSet<>();
        for (String root : catalog.components().keySet()) {
            if (finished.contains(root)) continue;
            Set<String> visiting = new HashSet<>();
            ArrayDeque<String> path = new ArrayDeque<>();
            visit(root, catalog, replacement, visiting, finished, path, issues);
        }
    }

    private void visit(String current, ComponentCatalog catalog, boolean replacement,
            Set<String> visiting, Set<String> finished, ArrayDeque<String> path,
            List<DesignSystemIssue> issues) {
        if (finished.contains(current) || !catalog.components().containsKey(current)) return;
        if (!visiting.add(current)) {
            String code = replacement ? "CATALOG_REPLACEMENT_CYCLE" : "COMPOSITION_CYCLE";
            issues.add(error(code, "순환 참조가 있습니다: " + String.join(" -> ", path) + " -> " + current, current));
            return;
        }
        path.addLast(current);
        ComponentCatalog.Entry entry = catalog.components().get(current);
        if (replacement) {
            if (entry.replacementLogicalType() != null) {
                visit(entry.replacementLogicalType(), catalog, true, visiting, finished, path, issues);
            }
        } else {
            for (String target : entry.composition()) {
                visit(target, catalog, false, visiting, finished, path, issues);
            }
        }
        path.removeLast();
        visiting.remove(current);
        finished.add(current);
    }

    private DesignSystemIssue error(String code, String message, String target) {
        return new DesignSystemIssue(code, DesignSystemIssue.Severity.ERROR, message, target);
    }
}
