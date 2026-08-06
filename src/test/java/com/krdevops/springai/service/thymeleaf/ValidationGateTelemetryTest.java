package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.service.observability.OperationalTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ValidationGateTelemetryTest {

    @Test
    void render와_a11y를_포함한_Gate_결과를_metric으로_기록할_수_있다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ValidationGateExecutor executor = new ValidationGateExecutor();
        executor.configureTelemetry(new OperationalTelemetry(registry));

        executor.validateThymeleafParse("<div>");

        assertThat(registry.get("springai.gate.executions.total")
                .tag("gate", "THYMELEAF_PARSE")
                .tag("severity", "BLOCK")
                .tag("outcome", "BLOCKED")
                .counter().count()).isEqualTo(1);
    }
}

