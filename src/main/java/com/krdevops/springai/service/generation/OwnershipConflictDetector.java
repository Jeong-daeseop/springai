package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 3-way 비교 결과와 Ownership 정책을 결합해 자동 병합 Conflict를 판정한다. */
@Service
public class OwnershipConflictDetector {
    public ConflictReport detect(List<ThreeWayRegionComparison> comparisons,
                                 Map<String, GenerationOwnershipManifest.RegionType> regionTypes) {
        List<Conflict> conflicts = (comparisons == null ? List.<ThreeWayRegionComparison>of() : comparisons).stream()
                .filter(comparison -> comparison.status() == ThreeWayRegionComparison.ChangeStatus.BOTH_CHANGED)
                .map(comparison -> {
                    GenerationOwnershipManifest.RegionType type = regionTypes == null
                            ? GenerationOwnershipManifest.RegionType.UNKNOWN
                            : regionTypes.getOrDefault(comparison.regionId(), GenerationOwnershipManifest.RegionType.UNKNOWN);
                    boolean autoMergeAllowed = type == GenerationOwnershipManifest.RegionType.GENERATED;
                    return new Conflict(comparison.regionId(), type, autoMergeAllowed,
                            autoMergeAllowed ? "Generated Region은 정책에 따라 재생성 검토 가능"
                                    : "Binding·Protected·Unknown Region은 자동 병합할 수 없습니다.");
                }).toList();
        return new ConflictReport(conflicts);
    }

    /** Binding·Protected Region Conflict가 있으면 Apply 경로에서 즉시 중단한다. */
    public void requireNoProtectedAutoMerge(ConflictReport report) {
        if (report == null) throw new IllegalArgumentException("ConflictReport는 필수입니다.");
        if (report.hasBlockingConflict()) {
            throw new ProtectedRegionMergeException(report);
        }
    }

    public record ConflictReport(List<Conflict> conflicts) {
        public ConflictReport { conflicts = List.copyOf(conflicts == null ? List.of() : conflicts); }
        public boolean hasBlockingConflict() { return conflicts.stream().anyMatch(conflict -> !conflict.autoMergeAllowed()); }
    }

    public record Conflict(String regionId, GenerationOwnershipManifest.RegionType regionType,
                           boolean autoMergeAllowed, String reason) { }

    public static final class ProtectedRegionMergeException extends IllegalStateException {
        private final ConflictReport report;
        public ProtectedRegionMergeException(ConflictReport report) {
            super("Binding·Protected·Unknown Region 자동 병합이 금지되었습니다.");
            this.report = report;
        }
        public ConflictReport report() { return report; }
    }
}
