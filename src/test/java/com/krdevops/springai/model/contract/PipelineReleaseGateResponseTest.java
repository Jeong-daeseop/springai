package com.krdevops.springai.model.contract;

import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class PipelineReleaseGateResponseTest {
    @Test void response_projectsReadinessForApi() {
        var readiness = new PipelineReleaseReadiness().evaluate(Map.of("build", true, "render", false));
        var response = PipelineReleaseGateResponse.from(readiness);
        assertThat(response.ready()).isFalse();
        assertThat(response.failedGateNames()).containsExactly("render");
    }
}
