package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 정확한 Catalog/Registry 버전을 결합하고 Pattern composition을 원자 Binding으로 해석한다. */
@Service
public class ResolvedComponentRegistryService {

    private final ComponentCatalogLoader catalogLoader;
    private final ComponentRegistryBindingValidator bindingValidator;
    private final ComponentRegistryOptionalFallbackPolicy fallbackPolicy;

    public ResolvedComponentRegistryService(
            ComponentCatalogLoader catalogLoader,
            ComponentRegistryBindingValidator bindingValidator) {
        this(catalogLoader, bindingValidator, new ComponentRegistryOptionalFallbackPolicy());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ResolvedComponentRegistryService(
            ComponentCatalogLoader catalogLoader,
            ComponentRegistryBindingValidator bindingValidator,
            ComponentRegistryOptionalFallbackPolicy fallbackPolicy) {
        this.catalogLoader = catalogLoader;
        this.bindingValidator = bindingValidator;
        this.fallbackPolicy = fallbackPolicy;
    }

    /** R2-T03: Optional 누락을 Preview에서만 Warning fallback으로 판정한다. */
    public PreviewResult preview(ComponentRegistrySnapshotV3 registry, Set<String> requestedLogicalTypes) {
        if (registry == null) {
            return new PreviewResult(null, List.of(new DesignSystemIssue("REGISTRY_NULL",
                    DesignSystemIssue.Severity.ERROR, "Registry Snapshot은 필수입니다.", null)));
        }
        var loaded = catalogLoader.load(registry.catalogVersion());
        List<DesignSystemIssue> issues = new ArrayList<>(
                bindingValidator.validate(loaded.catalog(), loaded.contentHash(), registry, false));
        for (String requested : requestedLogicalTypes == null ? Set.<String>of() : requestedLogicalTypes) {
            var entry = loaded.catalog().components().get(requested);
            if (entry != null && entry.atomicComponent()) {
                var decision = fallbackPolicy.decide(entry, registry.bindings().get(requested), true);
                if (decision.issue() != null) issues.add(new DesignSystemIssue(decision.issue().code(),
                        decision.issue().severity(), decision.issue().message(), requested));
            }
        }
        boolean blocked = issues.stream().anyMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                || issue.severity() == DesignSystemIssue.Severity.FATAL);
        ResolvedComponentRegistry resolved = null;
        if (!blocked && registry.approved()) {
            resolved = resolve(registry, requestedLogicalTypes);
        }
        return new PreviewResult(resolved, List.copyOf(issues));
    }

    public record PreviewResult(ResolvedComponentRegistry resolved, List<DesignSystemIssue> issues) {
        public PreviewResult { issues = List.copyOf(issues); }
    }

    public ResolvedComponentRegistry resolve(
            ComponentRegistrySnapshotV3 registry, Set<String> requestedLogicalTypes) {
        if (registry == null) {
            throw new ResolutionException(List.of(new DesignSystemIssue("REGISTRY_NULL",
                    DesignSystemIssue.Severity.ERROR, "Registry Snapshot은 필수입니다.", null)));
        }
        ComponentCatalogLoader.LoadedCatalog loaded = catalogLoader.load(registry.catalogVersion());
        List<DesignSystemIssue> issues = bindingValidator.validate(loaded.catalog(), loaded.contentHash(), registry);
        if (hasBlockingIssue(issues)) throw new ResolutionException(issues);

        Map<String, String> aliases = aliasMap(loaded.catalog());
        Map<String, ResolvedComponentRegistry.ResolvedEntry> resolved = new LinkedHashMap<>();
        for (String requested : requestedLogicalTypes == null ? Set.<String>of() : requestedLogicalTypes) {
            String canonical = loaded.catalog().components().containsKey(requested) ? requested : aliases.get(requested);
            if (canonical == null) {
                issues = new ArrayList<>(issues);
                issues.add(new DesignSystemIssue("UNKNOWN_LOGICAL_TYPE", DesignSystemIssue.Severity.ERROR,
                        "Catalog에 없는 논리 타입입니다.", requested));
                throw new ResolutionException(issues);
            }
            List<ResolvedComponentRegistry.AtomicBinding> atomic = new ArrayList<>();
            List<String> path = new ArrayList<>();
            expand(canonical, loaded.catalog(), registry, new HashSet<>(), path, atomic);
            resolved.put(requested, new ResolvedComponentRegistry.ResolvedEntry(
                    requested, canonical, loaded.catalog().components().get(canonical), atomic, path));
        }
        return new ResolvedComponentRegistry(
                loaded.catalog().contractVersion(), loaded.contentHash(), registry.profileId(),
                registry.profileVersion(), registry.registryVersion(), registry.contentHash(), resolved);
    }

    private void expand(String logicalType, ComponentCatalog catalog, ComponentRegistrySnapshotV3 registry,
            Set<String> visiting, List<String> path, List<ResolvedComponentRegistry.AtomicBinding> result) {
        if (!visiting.add(logicalType)) {
            throw new ResolutionException(List.of(new DesignSystemIssue("COMPOSITION_CYCLE",
                    DesignSystemIssue.Severity.ERROR, "합성 경로에 순환 참조가 있습니다.", logicalType)));
        }
        path.add(logicalType);
        ComponentCatalog.Entry entry = catalog.components().get(logicalType);
        if (entry.atomicComponent()) {
            ComponentRegistrySnapshotV3.Binding binding = registry.bindings().get(logicalType);
            if (binding != null) result.add(new ResolvedComponentRegistry.AtomicBinding(logicalType, entry, binding));
        } else {
            for (String child : entry.composition()) expand(child, catalog, registry, visiting, path, result);
        }
        visiting.remove(logicalType);
    }

    private Map<String, String> aliasMap(ComponentCatalog catalog) {
        Map<String, String> aliases = new HashMap<>();
        catalog.components().forEach((logicalType, entry) ->
                entry.aliases().forEach(alias -> aliases.put(alias, logicalType)));
        return aliases;
    }

    private boolean hasBlockingIssue(List<DesignSystemIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                || issue.severity() == DesignSystemIssue.Severity.FATAL);
    }

    public static class ResolutionException extends IllegalArgumentException {
        private final List<DesignSystemIssue> issues;

        public ResolutionException(List<DesignSystemIssue> issues) {
            super(issues.isEmpty() ? "Component Registry 해석 실패" : issues.get(0).message());
            this.issues = List.copyOf(issues);
        }

        public List<DesignSystemIssue> issues() {
            return issues;
        }
    }
}
