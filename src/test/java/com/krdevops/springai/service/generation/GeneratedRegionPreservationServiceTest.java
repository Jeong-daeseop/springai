package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class GeneratedRegionPreservationServiceTest {
    @Test
    void Generated_Region_밖의_Current_변경을_보존계획으로_분리한다() {
        String a = "a".repeat(64), b = "b".repeat(64);
        var plan = new GeneratedRegionPreservationService().plan(
                List.of(ThreeWayRegionComparison.compare("user.notes", a, b, a)),
                Map.of("user.notes", GenerationOwnershipManifest.RegionType.UNKNOWN));
        assertThat(plan.hasPreservedRegions()).isTrue();
        assertThat(plan.regions()).extracting("regionId").containsExactly("user.notes");
    }
}
