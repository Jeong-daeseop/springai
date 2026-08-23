package com.krdevops.springai.service.generation;

import com.krdevops.springai.model.generation.ThreeWayRegionComparison.ChangeStatus;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ThreeWayRegionComparisonServiceTest {
    @Test
    void Base_Current_New_변경상태를_구분한다() {
        String a = "a".repeat(64), b = "b".repeat(64), c = "c".repeat(64);
        var result = new ThreeWayRegionComparisonService().compare(
                Map.of("unchanged", a, "current", a, "new", a, "both", a),
                Map.of("unchanged", a, "current", b, "new", a, "both", b),
                Map.of("unchanged", a, "current", a, "new", c, "both", c));
        assertThat(result).extracting(r -> r.status()).containsExactly(
                ChangeStatus.BOTH_CHANGED, ChangeStatus.CURRENT_ONLY, ChangeStatus.NEW_ONLY, ChangeStatus.UNCHANGED);
    }
}
