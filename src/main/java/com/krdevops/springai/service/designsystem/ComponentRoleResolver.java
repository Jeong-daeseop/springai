package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.figma.ComponentResolutionContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Semantic Role을 정확히 하나의 CURRENT Component Contract로 해석한다. */
@Component
public class ComponentRoleResolver {

    public Resolution resolve(ComponentRegistry registry, ComponentResolutionContext context) {
        if (registry == null || context == null) {
            return new Resolution(Status.NOT_RESOLVED, null, null, List.of());
        }
        SemanticRole role = context.role();
        List<Candidate> candidates = registry.components().entrySet().stream()
                .filter(entry -> entry.getValue().roles().contains(role))
                .filter(entry -> entry.getValue().currentForGeneration())
                .filter(entry -> entry.getValue().supportedPlatforms().isEmpty()
                        || entry.getValue().supportedPlatforms().contains(context.platform()))
                .map(entry -> new Candidate(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Candidate::logicalType))
                .toList();
        if (candidates.isEmpty()) return new Resolution(Status.NOT_RESOLVED, null, null, candidates);
        if (candidates.size() > 1) return new Resolution(Status.AMBIGUOUS, null, null, candidates);
        Candidate candidate = candidates.get(0);
        return new Resolution(Status.RESOLVED, candidate.logicalType(), candidate.entry(), candidates);
    }

    public enum Status { RESOLVED, NOT_RESOLVED, AMBIGUOUS }

    public record Candidate(String logicalType, ComponentRegistryEntry entry) {}

    public record Resolution(
            Status status,
            String logicalType,
            ComponentRegistryEntry entry,
            List<Candidate> candidates
    ) {
        public boolean resolved() { return status == Status.RESOLVED; }
        public String errorCode() {
            return status == Status.AMBIGUOUS ? "ROLE_AMBIGUOUS" : status == Status.NOT_RESOLVED
                    ? "ROLE_NOT_RESOLVED" : null;
        }
    }
}
