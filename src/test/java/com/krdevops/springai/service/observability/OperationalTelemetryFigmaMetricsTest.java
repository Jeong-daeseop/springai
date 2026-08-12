package com.krdevops.springai.service.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** KRV-074: Figma Role/Variant 해석 관련 운영 지표가 등록되고 finite tag로 정규화되는지 검증한다. */
class OperationalTelemetryFigmaMetricsTest {

    @Test
    void figmaMetricsAreRegisteredWithNormalizedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);

        telemetry.figmaRoleResolutionFailure("ROLE_AMBIGUOUS");
        telemetry.figmaVariantResolutionFailure("VARIANT_RULE_NOT_FOUND");
        telemetry.figmaComponentPropertyDrift("COMPONENT_PROPERTY_DRIFT");
        telemetry.figmaFallbackAttempt("PLACEHOLDER");
        telemetry.figmaVisualGateFailure("LAYOUT");
        telemetry.figmaResolutionDuration("SUCCESS", 1_000_000L);

        assertThat(registry.find("figma_role_resolution_failure_total").counter()).isNotNull();
        assertThat(registry.find("figma_variant_resolution_failure_total").counter()).isNotNull();
        assertThat(registry.find("figma_component_property_drift_total").counter()).isNotNull();
        assertThat(registry.find("figma_fallback_attempt_total").counter()).isNotNull();
        assertThat(registry.find("figma_visual_gate_failure_total").counter()).isNotNull();
        assertThat(registry.find("figma_resolution_duration_seconds").timer()).isNotNull();
    }

    @Test
    void unknownErrorCodeFallsBackToOtherTagInsteadOfUnboundedCardinality() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);

        telemetry.figmaRoleResolutionFailure("SOME_UNEXPECTED_NEW_CODE");

        assertThat(registry.find("figma_role_resolution_failure_total")
                .tag("error_code", "OTHER").counter()).isNotNull();
    }
}
