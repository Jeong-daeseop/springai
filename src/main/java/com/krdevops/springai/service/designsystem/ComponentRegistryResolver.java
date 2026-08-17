package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.ResolvedComponentRegistry;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 논리 컴포넌트 ID의 직접·alias·replacement 해석을 한 곳에서 수행한다. */
@Service
public class ComponentRegistryResolver {

    private final ResolvedComponentRegistryService resolvedRegistryService;

    @Autowired
    public ComponentRegistryResolver(ResolvedComponentRegistryService resolvedRegistryService) {
        this.resolvedRegistryService = resolvedRegistryService;
    }

    public ComponentRegistryResolver() {
        this.resolvedRegistryService = null;
    }

    /** R2-010: v3 입력은 신규 Resolver로 위임해 Legacy 직접 해석을 중복하지 않는다. */
    public ResolvedComponentRegistry resolve(ComponentRegistrySnapshotV3 registry, Set<String> requestedLogicalTypes) {
        if (resolvedRegistryService == null) {
            throw new IllegalStateException("Resolved Registry Service가 연결되지 않았습니다.");
        }
        return resolvedRegistryService.resolve(registry, requestedLogicalTypes);
    }

    public Resolution resolve(ComponentRegistry registry, String requestedLogicalType) {
        if (registry == null || requestedLogicalType == null || requestedLogicalType.isBlank()) {
            return missing(requestedLogicalType);
        }

        Map<String, String> aliasOwners = new HashMap<>();
        registry.components().forEach((logicalType, entry) ->
                entry.aliases().forEach(alias -> aliasOwners.putIfAbsent(alias, logicalType)));

        String current = registry.components().containsKey(requestedLogicalType)
                ? requestedLogicalType
                : aliasOwners.get(requestedLogicalType);
        if (current == null) {
            return missing(requestedLogicalType);
        }

        boolean aliasUsed = !current.equals(requestedLogicalType);
        List<String> path = new ArrayList<>();
        path.add(requestedLogicalType);
        if (aliasUsed) {
            path.add(current);
        }

        Set<String> visited = new HashSet<>();
        boolean replacementUsed = false;
        while (current != null) {
            if (!visited.add(current)) {
                return new Resolution(requestedLogicalType, current, null,
                        ResolutionKind.CYCLE, List.copyOf(path));
            }
            ComponentRegistryEntry entry = registry.components().get(current);
            if (entry == null) {
                return new Resolution(requestedLogicalType, current, null,
                        ResolutionKind.MISSING, List.copyOf(path));
            }
            if (entry.lifecycleStatus() != ComponentRegistryEntry.LifecycleStatus.DEPRECATED) {
                if (entry.lifecycleStatus() == ComponentRegistryEntry.LifecycleStatus.REMOVED) {
                    return new Resolution(requestedLogicalType, current, null,
                            ResolutionKind.MISSING, List.copyOf(path));
                }
                ResolutionKind kind = replacementUsed
                        ? ResolutionKind.REPLACEMENT
                        : aliasUsed ? ResolutionKind.ALIAS : ResolutionKind.DIRECT;
                return new Resolution(requestedLogicalType, current, entry, kind, List.copyOf(path));
            }
            replacementUsed = true;
            current = entry.replacementLogicalType();
            if (current == null || current.isBlank()) {
                return new Resolution(requestedLogicalType, null, null,
                        ResolutionKind.MISSING, List.copyOf(path));
            }
            path.add(current);
        }
        return missing(requestedLogicalType);
    }

    private Resolution missing(String requestedLogicalType) {
        return new Resolution(requestedLogicalType, null, null,
                ResolutionKind.MISSING, List.of(requestedLogicalType));
    }

    public enum ResolutionKind { DIRECT, ALIAS, REPLACEMENT, MISSING, CYCLE }

    public record Resolution(
            String requestedLogicalType,
            String resolvedLogicalType,
            ComponentRegistryEntry entry,
            ResolutionKind kind,
            List<String> path
    ) {
        public boolean resolved() {
            return entry != null
                    && kind != ResolutionKind.MISSING
                    && kind != ResolutionKind.CYCLE;
        }
    }
}
