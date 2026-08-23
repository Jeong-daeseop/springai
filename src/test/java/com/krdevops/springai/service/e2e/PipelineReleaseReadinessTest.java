package com.krdevops.springai.service.e2e;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineReleaseReadinessTest {
    @Test void readiness_exposesFailedGateNames() {
        var readiness = new PipelineReleaseReadiness().evaluate(Map.of("build", true, "visual", false));
        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.failedGateNames()).containsExactly("visual");
    }

    @Test void readiness_rejectsBlankGateName() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                new PipelineReleaseReadiness().evaluate(Map.of("", true)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void readiness_treatsNullGateAsFailure() {
        var gates = new java.util.HashMap<String, Boolean>();
        gates.put("build", null);
        var readiness = new PipelineReleaseReadiness().evaluate(gates);
        assertThat(readiness.ready()).isFalse();
        assertThat(readiness.failedGateNames()).containsExactly("build");
    }
}
