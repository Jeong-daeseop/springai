package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Generated Region 밖의 사용자 Current 변경을 New 생성 결과에서 보존한다. */
@Service
public class GeneratedRegionPreservationService {
    public PreservationPlan plan(List<ThreeWayRegionComparison> comparisons,
                                 Map<String, GenerationOwnershipManifest.RegionType> regionTypes) {
        List<PreservedRegion> regions = (comparisons == null ? List.<ThreeWayRegionComparison>of() : comparisons).stream()
                .filter(comparison -> comparison.status() == ThreeWayRegionComparison.ChangeStatus.CURRENT_ONLY)
                .map(comparison -> new PreservedRegion(comparison.regionId(),
                        regionTypes == null ? GenerationOwnershipManifest.RegionType.UNKNOWN
                                : regionTypes.getOrDefault(comparison.regionId(), GenerationOwnershipManifest.RegionType.UNKNOWN),
                        comparison.currentHash()))
                .toList();
        return new PreservationPlan(regions);
    }

    public record PreservationPlan(List<PreservedRegion> regions) {
        public PreservationPlan { regions = List.copyOf(regions == null ? List.of() : regions); }
        public boolean hasPreservedRegions() { return !regions.isEmpty(); }
    }
    public record PreservedRegion(String regionId, GenerationOwnershipManifest.RegionType regionType,
                                  String currentHash) { }
}
