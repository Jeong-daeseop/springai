package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class OwnershipConflictDetectorTest {
    @Test
    void Binding과_Protected_변경은_자동병합을_차단한다() {
        String a = "a".repeat(64), b = "b".repeat(64), c = "c".repeat(64);
        var detector = new OwnershipConflictDetector();
        var report = detector.detect(List.of(
                ThreeWayRegionComparison.compare("binding.vo", a, b, c),
                ThreeWayRegionComparison.compare("generated.controller", a, b, c)),
                Map.of("binding.vo", GenerationOwnershipManifest.RegionType.BINDING,
                        "generated.controller", GenerationOwnershipManifest.RegionType.GENERATED));
        assertThat(report.conflicts()).hasSize(2);
        assertThat(report.hasBlockingConflict()).isTrue();
        assertThat(report.conflicts().stream().filter(cnf -> cnf.regionId().equals("generated.controller"))
                .findFirst().orElseThrow().autoMergeAllowed()).isTrue();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> detector.requireNoProtectedAutoMerge(report))
                .isInstanceOf(OwnershipConflictDetector.ProtectedRegionMergeException.class);
    }
}
