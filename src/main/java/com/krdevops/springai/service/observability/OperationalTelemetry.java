package com.krdevops.springai.service.observability;

import com.krdevops.springai.config.observability.ObservabilityContextHolder;
import com.krdevops.springai.model.artifact.ArtifactReconciliationReport;
import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.service.resilience.ExternalDependency;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 모든 metric tag를 유한 집합으로 정규화하는 WP10 단일 telemetry 진입점. */
@Component
public class OperationalTelemetry {

    private static final Logger log = LoggerFactory.getLogger("springai.telemetry");
    private static final Set<String> OPERATION_TYPES = Set.of(
            "THYMELEAF_PROJECT", "FIGMA_DESIGN", "DESIGN_SYSTEM", "GENERATION");
    private static final Set<String> ARTIFACT_TYPES = Set.of(
            "THYMELEAF_PREVIEW", "FIGMA_EXPORT_BUNDLE", "FIGMA_SCREEN", "DESIGN_ANALYSIS", "WEB_CAPTURE",
            "THYMELEAF_VISUAL_BASELINE", "THYMELEAF_BASELINE_APPROVAL", "THYMELEAF_VALIDATION_REPORT");
    private static final Set<String> OPERATION_STATUSES = Set.of(
            "NONE", "ANALYZED", "CONTRACT_READY", "PREVIEW_READY", "APPROVED", "APPLY_REQUIRED",
            "APPLIED", "VALIDATED", "REJECTED", "CONFLICT", "FAILED", "OTHER");
    private static final Set<String> OUTCOMES = Set.of(
            "SUCCESS", "FAILURE", "DENIED", "REJECTED", "TIMEOUT", "CIRCUIT_OPEN", "BLOCKED", "WARN", "OTHER");
    private static final Set<String> OPERATION_OUTCOMES = Set.of(
            "CONFLICT", "REJECTED", "ROLLBACK", "ROLLBACK_FAILED");
    /** KRV-074: 호출자가 오류 코드를 role/variant/drift 지표 중 어디로 보낼지 분류할 때 재사용하는 목록. */
    public static final Set<String> ROLE_RESOLUTION_ERROR_CODES = Set.of("ROLE_NOT_RESOLVED", "ROLE_AMBIGUOUS");
    public static final Set<String> VARIANT_RESOLUTION_ERROR_CODES = Set.of(
            "VARIANT_RULE_NOT_FOUND", "VARIANT_RULE_AMBIGUOUS", "VARIANT_AXIS_NOT_DECLARED",
            "VARIANT_VALUE_NOT_ALLOWED", "VARIANT_NOT_RESOLVED", "VARIANT_AMBIGUOUS",
            "REQUIRED_VARIANT_AXIS_MISSING", "VARIANT_RULE_SET_NOT_PUBLISHED");
    public static final Set<String> COMPONENT_PROPERTY_DRIFT_CODES = Set.of(
            "COMPONENT_PROPERTY_DRIFT", "COMPONENT_VARIANT_DRIFT", "COMPONENT_VARIANT_NAME_DRIFT",
            "COMPONENT_UNDECLARED_PROPERTY_DRIFT", "REQUIRED_COMPONENT_PROPERTY_MISSING");
    private static final Set<String> FALLBACK_TYPES = Set.of(
            "FIRST_VARIANT", "FIRST_COMPONENT", "PLACEHOLDER", "LOCAL_NAME");
    private static final Set<String> VISUAL_GATE_FAILURE_REASONS = Set.of(
            "LAYOUT", "ACCESSIBILITY", "VISUAL_DIFF_THRESHOLD_EXCEEDED", "VISUAL_BASELINE_MISSING");
    private static final Set<String> RESOLUTION_STAGES = Set.of("SUCCESS", "FAILURE");
    private static final Set<String> REFINEMENT_OUTCOMES = Set.of("APPLIED", "EXCLUDED", "CONFLICT", "BLOCKED");

    private final MeterRegistry registry;

    public OperationalTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    public void toolCall(String toolName, String risk, String outcome, long durationNanos) {
        String family = toolFamily(toolName);
        String normalizedRisk = enumValue(risk, Set.of("READ", "EXTERNAL", "DB_WRITE", "FILE_WRITE", "APPLY"));
        String normalizedOutcome = outcome(outcome);
        counter("springai.tool.calls.total", "tool_family", family, "risk", normalizedRisk,
                "outcome", normalizedOutcome).increment();
        timer("springai.tool.calls.duration", "tool_family", family, "risk", normalizedRisk,
                "outcome", normalizedOutcome).record(durationNanos, TimeUnit.NANOSECONDS);
        event("tool_call", null, null, "tool_family=" + family + " outcome=" + normalizedOutcome);
    }

    public void operationTransition(String operationId, String operationType, String fromStatus,
                                    String toStatus, String eventType) {
        String type = operationType(operationType);
        String from = status(fromStatus);
        String to = status(toStatus);
        String outcome = operationOutcome(eventType, to);
        counter("springai.operation.transitions.total", "operation_type", type,
                "from", from, "to", to, "outcome", outcome).increment();
        if (Set.of("CONFLICT", "REJECTED", "ROLLBACK", "ROLLBACK_FAILED").contains(outcome)) {
            counter("springai.operation.outcomes.total", "operation_type", type,
                    "outcome", enumValue(outcome, OPERATION_OUTCOMES)).increment();
        }
        event("operation_transition", operationId, null,
                "operation_type=" + type + " from=" + from + " to=" + to + " outcome=" + outcome);
    }

    public void artifactAction(String artifactId, String artifactType, String action,
                               String outcome, long durationNanos) {
        String type = artifactType(artifactType);
        String normalizedAction = enumValue(action, Set.of("INGEST", "LINK", "READ", "ORPHAN", "CLEANUP"));
        String normalizedOutcome = outcome(outcome);
        counter("springai.artifact.actions.total", "artifact_type", type,
                "action", normalizedAction, "outcome", normalizedOutcome).increment();
        timer("springai.artifact.actions.duration", "artifact_type", type,
                "action", normalizedAction, "outcome", normalizedOutcome)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        event("artifact_action", null, artifactId,
                "artifact_type=" + type + " action=" + normalizedAction + " outcome=" + normalizedOutcome);
    }

    public void reconciliation(ArtifactReconciliationReport report, long durationNanos) {
        String mode = report.dryRun() ? "DRY_RUN" : "EXECUTE";
        counter("springai.artifact.reconciliation.runs.total", "mode", mode,
                "outcome", "SUCCESS").increment();
        counter("springai.artifact.reconciliation.items.total", "mode", mode,
                "kind", "ORPHAN").increment(report.orphanContentHashes().size());
        counter("springai.artifact.reconciliation.items.total", "mode", mode,
                "kind", "MISSING").increment(report.missingArtifacts().size());
        counter("springai.artifact.reconciliation.items.total", "mode", mode,
                "kind", "QUARANTINED").increment(report.quarantinedContentHashes().size());
        timer("springai.artifact.reconciliation.duration", "mode", mode)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        event("artifact_reconciliation", null, null,
                "mode=" + mode + " clean=" + report.isClean());
    }

    public void reconciliationFailure(boolean dryRun, long durationNanos) {
        String mode = dryRun ? "DRY_RUN" : "EXECUTE";
        counter("springai.artifact.reconciliation.runs.total", "mode", mode,
                "outcome", "FAILURE").increment();
        timer("springai.artifact.reconciliation.duration", "mode", mode)
                .record(durationNanos, TimeUnit.NANOSECONDS);
        event("artifact_reconciliation", null, null,
                "mode=" + mode + " outcome=FAILURE");
    }

    public void externalCall(ExternalDependency dependency, String outcome, long durationNanos) {
        String normalizedOutcome = outcome(outcome);
        counter("springai.external.calls.total", "dependency", dependency.name(),
                "outcome", normalizedOutcome).increment();
        timer("springai.external.calls.duration", "dependency", dependency.name(),
                "outcome", normalizedOutcome).record(durationNanos, TimeUnit.NANOSECONDS);
        if ("TIMEOUT".equals(normalizedOutcome)) {
            counter("springai.external.timeouts.total", "dependency", dependency.name()).increment();
        }
        if ("CIRCUIT_OPEN".equals(normalizedOutcome)) {
            counter("springai.external.circuit.open.total", "dependency", dependency.name()).increment();
        }
        event("external_call", null, null,
                "dependency=" + dependency.name() + " outcome=" + normalizedOutcome);
    }

    public void registerCircuitGauge(ExternalDependency dependency, Supplier<Number> state) {
        Gauge.builder("springai.external.circuit.state", state, supplier -> supplier.get().doubleValue())
                .description("0=CLOSED, 1=HALF_OPEN, 2=OPEN")
                .tag("dependency", dependency.name())
                .register(registry);
    }

    public void gate(ValidationGateType gateType, GateSeverity severity, boolean passed, Duration duration) {
        String outcome = passed ? "SUCCESS" : severity == GateSeverity.BLOCK ? "BLOCKED" : "WARN";
        counter("springai.gate.executions.total", "gate", gateType.name(),
                "severity", severity.name(), "outcome", outcome).increment();
        timer("springai.gate.executions.duration", "gate", gateType.name(),
                "severity", severity.name(), "outcome", outcome)
                .record(duration == null ? Duration.ZERO : duration);
        event("validation_gate", null, null,
                "gate=" + gateType.name() + " severity=" + severity.name() + " outcome=" + outcome);
    }

    /** KRV-074: Semantic Role이 정확히 하나로 해석되지 않은 실패(ROLE_NOT_RESOLVED/ROLE_AMBIGUOUS)를 기록한다. */
    public void figmaRoleResolutionFailure(String errorCode) {
        counter("figma_role_resolution_failure_total",
                "error_code", enumValue(errorCode, ROLE_RESOLUTION_ERROR_CODES)).increment();
    }

    /** KRV-074: Variant Rule이 정확히 하나로 해석되지 않은 실패를 기록한다. */
    public void figmaVariantResolutionFailure(String errorCode) {
        counter("figma_variant_resolution_failure_total",
                "error_code", enumValue(errorCode, VARIANT_RESOLUTION_ERROR_CODES)).increment();
    }

    /** KRV-074: Registry Contract와 실제 Figma Library 사이의 Property/Variant Drift를 기록한다. */
    public void figmaComponentPropertyDrift(String driftCode) {
        counter("figma_component_property_drift_total",
                "drift_code", enumValue(driftCode, COMPONENT_PROPERTY_DRIFT_CODES)).increment();
    }

    /** KRV-074: Plugin Apply 경로에서 금지된 폴백(첫 Variant/첫 Component/Placeholder/로컬 이름)이 시도된 횟수를 기록한다. */
    public void figmaFallbackAttempt(String fallbackType) {
        counter("figma_fallback_attempt_total",
                "fallback_type", enumValue(fallbackType, FALLBACK_TYPES)).increment();
    }

    /** KRV-074: Layout/Accessibility/Visual Gate 실패를 기록한다. */
    public void figmaVisualGateFailure(String reason) {
        counter("figma_visual_gate_failure_total",
                "reason", enumValue(reason, VISUAL_GATE_FAILURE_REASONS)).increment();
    }

    /** KRV-074: Role·Variant 전체 해석 파이프라인(KrdsComponentResolutionService.resolve) 처리 시간을 기록한다. */
    public void figmaResolutionDuration(String outcome, long durationNanos) {
        timer("figma_resolution_duration_seconds", "outcome", enumValue(outcome, RESOLUTION_STAGES))
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * MR-Q06: 재적용 시도 1건에 대해 적용·제외·충돌·차단 건수를 한 번에 기록한다. `outcome`
     * 태그로 4분류하며, 값은 Prometheus에서 {@code rate()}로 적용률·충돌률·차단률을 계산한다.
     */
    public void figmaRefinementApplyOutcome(String outcome, int count) {
        if (count <= 0) return;
        counter("figma_refinement_patches_total", "outcome", enumValue(outcome, REFINEMENT_OUTCOMES))
                .increment(count);
    }

    /** MR-Q06: Refinement가 포함된 Apply가 Gate 실패 등으로 전체 Rollback된 횟수를 기록한다. */
    public void figmaRefinementRollback() {
        counter("figma_refinement_rollback_total").increment();
    }

    public MeterRegistry registry() { return registry; }

    public static String toolFamily(String name) {
        String value = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (value.contains("artifact") || value.contains("capture") || value.contains("figma")) return "DESIGN";
        if (value.contains("validate") || value.contains("health") || value.contains("scan")) return "VALIDATION";
        if (value.contains("generate") || value.contains("template") || value.contains("source")) return "GENERATION";
        if (value.contains("workflow") || value.contains("approve") || value.contains("apply")) return "WORKFLOW";
        if (value.contains("schema") || value.contains("sql") || value.contains("rag") || value.contains("employee")) return "DATA";
        if (value.contains("date") || value.contains("path") || value.contains("menu") || value.contains("auth")) return "UTILITY";
        return "OTHER";
    }

    public static String operationType(String value) { return enumValue(value, OPERATION_TYPES); }
    public static String artifactType(String value) { return enumValue(value, ARTIFACT_TYPES); }

    private static String status(String value) {
        if (value == null || value.isBlank()) return "NONE";
        return enumValue(value, OPERATION_STATUSES);
    }

    private static String operationOutcome(String eventType, String toStatus) {
        String event = eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);
        if (event.contains("ROLLBACK_FAILED")) return "ROLLBACK_FAILED";
        if (event.contains("ROLLBACK")) return "ROLLBACK";
        if (event.contains("CONFLICT") || "CONFLICT".equals(toStatus)) return "CONFLICT";
        if (event.contains("REJECT") || "REJECTED".equals(toStatus)) return "REJECTED";
        if (event.contains("FAIL") || "FAILED".equals(toStatus)) return "FAILURE";
        return "SUCCESS";
    }

    private static String outcome(String value) {
        String normalized = value == null ? "OTHER" : value.toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLLBACK")) return normalized.equals("ROLLBACK_FAILED") ? "FAILURE" : "FAILURE";
        if (normalized.equals("CONFLICT")) return "FAILURE";
        return OUTCOMES.contains(normalized) ? normalized : "OTHER";
    }

    private static String enumValue(String value, Set<String> allowed) {
        String normalized = value == null ? "OTHER" : value.toUpperCase(Locale.ROOT);
        return allowed.contains(normalized) ? normalized : "OTHER";
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).publishPercentileHistogram().register(registry);
    }

    private void event(String eventName, String operationId, String artifactId, String message) {
        try (var contextScope = ObservabilityContextHolder.open(ObservabilityContextHolder.current());
             var operationScope = scope(operationId, true);
             var artifactScope = scope(artifactId, false);
             var eventScope = ObservabilityContextHolder.openEvent(eventName)) {
            log.info("{}", message);
        }
    }

    private ObservabilityContextHolder.Scope scope(String id, boolean operation) {
        if (id == null) return () -> { };
        return operation ? ObservabilityContextHolder.openOperation(id)
                : ObservabilityContextHolder.openArtifact(id);
    }
}
