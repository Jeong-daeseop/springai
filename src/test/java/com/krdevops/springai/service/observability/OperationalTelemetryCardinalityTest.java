package com.krdevops.springai.service.observability;

import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalTelemetryCardinalityTest {

    @Test
    void 사용자_ID_경로_입력값은_metric_label이_되지_않는다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);

        for (int i = 0; i < 50; i++) {
            String dynamic = "customer-file-key-/private/path/" + i;
            telemetry.toolCall(dynamic, "READ", "SUCCESS", 1);
            telemetry.operationTransition("operation-" + i, dynamic, dynamic, dynamic, dynamic);
            telemetry.artifactAction("artifact-" + i, dynamic, "INGEST", "SUCCESS", 1);
        }
        telemetry.gate(ValidationGateType.ACCESSIBILITY, GateSeverity.BLOCK, false, Duration.ofMillis(3));

        Set<String> tagValues = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue())
                .collect(java.util.stream.Collectors.toSet());

        assertThat(tagValues).doesNotContain("operation-1", "artifact-1", "customer-file-key-/private/path/1");
        assertThat(tagValues).allMatch(value -> value.matches("[A-Z_]+"));
        assertThat(registry.getMeters().size()).isLessThan(20);
        assertThat(OperationalTelemetry.operationType("tenant-123")).isEqualTo("OTHER");
        assertThat(OperationalTelemetry.artifactType("/secret/file.txt")).isEqualTo("OTHER");
    }
}

