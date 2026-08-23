package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 3-way 비교·Ownership 결과를 Preview 전용 Semantic Merge Plan으로 조립한다. */
@Service
public class SemanticMergePlanService {
    private final OwnershipConflictDetector conflictDetector;
    private final GeneratedRegionPreservationService preservationService;

    public SemanticMergePlanService(OwnershipConflictDetector conflictDetector,
                                    GeneratedRegionPreservationService preservationService) {
        this.conflictDetector = conflictDetector;
        this.preservationService = preservationService;
    }

    public SemanticMergePlan preview(List<ThreeWayRegionComparison> comparisons,
                                     Map<String, GenerationOwnershipManifest.RegionType> regionTypes) {
        var conflicts = conflictDetector.detect(comparisons, regionTypes);
        var preserved = preservationService.plan(comparisons, regionTypes);
        List<String> changed = (comparisons == null ? List.<ThreeWayRegionComparison>of() : comparisons).stream()
                .filter(c -> c.status() != ThreeWayRegionComparison.ChangeStatus.UNCHANGED)
                .map(ThreeWayRegionComparison::regionId).sorted().toList();
        return new SemanticMergePlan(changed, preserved.regions().stream().map(GeneratedRegionPreservationService.PreservedRegion::regionId).toList(),
                conflicts.conflicts().stream().map(OwnershipConflictDetector.Conflict::regionId).toList(), true,
                !conflicts.hasBlockingConflict());
    }

    public record SemanticMergePlan(List<String> changedRegionIds, List<String> preservedRegionIds,
                                    List<String> conflictRegionIds, boolean previewOnly, boolean applyAllowed) {
        public SemanticMergePlan {
            changedRegionIds = List.copyOf(changedRegionIds == null ? List.of() : changedRegionIds);
            preservedRegionIds = List.copyOf(preservedRegionIds == null ? List.of() : preservedRegionIds);
            conflictRegionIds = List.copyOf(conflictRegionIds == null ? List.of() : conflictRegionIds);
        }
    }
}
