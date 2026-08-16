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

    @Test
    void refinementApplyOutcomeCountsAppliedExcludedConflictAndBlocked() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);

        telemetry.figmaRefinementApplyOutcome("APPLIED", 3);
        telemetry.figmaRefinementApplyOutcome("EXCLUDED", 1);
        telemetry.figmaRefinementApplyOutcome("CONFLICT", 2);
        telemetry.figmaRefinementApplyOutcome("BLOCKED", 1);
        telemetry.figmaRefinementRollback();

        assertThat(registry.find("figma_refinement_patches_total")
                .tag("outcome", "APPLIED").counter().count()).isEqualTo(3);
        assertThat(registry.find("figma_refinement_patches_total")
                .tag("outcome", "CONFLICT").counter().count()).isEqualTo(2);
        assertThat(registry.find("figma_refinement_rollback_total").counter().count()).isEqualTo(1);
    }

    @Test
    void refinementApplyOutcomeIgnoresZeroCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalTelemetry telemetry = new OperationalTelemetry(registry);

        telemetry.figmaRefinementApplyOutcome("APPLIED", 0);

        assertThat(registry.find("figma_refinement_patches_total").counter()).isNull();
    }
}
