package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.GenerationOwnershipManifest;
import com.krdevops.springai.model.generation.ThreeWayRegionComparison;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SemanticMergePlanServiceTest {
    @Test
    void Preview는_가능하지만_Conflict가_있으면_Apply는_허용하지_않는다() {
        String a = "a".repeat(64), b = "b".repeat(64), c = "c".repeat(64);
        var service = new SemanticMergePlanService(new OwnershipConflictDetector(), new GeneratedRegionPreservationService());
        var plan = service.preview(List.of(ThreeWayRegionComparison.compare("binding.vo", a, b, c)),
                Map.of("binding.vo", GenerationOwnershipManifest.RegionType.BINDING));
        assertThat(plan.previewOnly()).isTrue();
        assertThat(plan.applyAllowed()).isFalse();
        assertThat(plan.conflictRegionIds()).containsExactly("binding.vo");
    }
}
