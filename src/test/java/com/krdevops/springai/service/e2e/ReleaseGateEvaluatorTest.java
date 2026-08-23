package com.krdevops.springai.service.e2e;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class ReleaseGateEvaluatorTest {
    @Test void namedGates_reportActionableFailures() {
        var result = new ReleaseGateEvaluator().evaluateNamed(Map.of(
                "binding", true, "build", false, "render", false));
        assertThat(result.releasable()).isFalse();
        assertThat(result.failedGateNames()).containsExactlyInAnyOrder("build", "render");
    }
}
