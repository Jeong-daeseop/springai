package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.model.controlplane.ReadinessComparisonRecord;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.observability.PipelineMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.Instant;
import com.krdevops.springai.model.controlplane.ReadinessComparisonReport;

/** 기존 Boolean Gate 판정과 공통 Evidence 판정을 개인정보 없이 비교 관측한다. */
@Slf4j
@Component
public class ReadinessComparisonObserver {

    private final PipelineMetricsCollector metrics;
    private final ReadinessComparisonPort comparisonPort;

    public ReadinessComparisonObserver(PipelineMetricsCollector metrics) {
        this(metrics, new NoopReadinessComparisonPort());
    }

    @Autowired
    public ReadinessComparisonObserver(PipelineMetricsCollector metrics, ReadinessComparisonPort comparisonPort) {
        this.metrics = metrics;
        this.comparisonPort = comparisonPort;
    }

    public void observe(String operationId, GenerationSourceType sourceType,
                        PipelineReleaseReadiness.Readiness legacy, ReleaseReadiness common) {
        metrics.increment("generation_readiness_comparison_total");
        boolean matched = legacy.ready() == common.releaseReady();
        MismatchReason reason = matched ? null : classify(legacy, common);
        if (!matched) {
            metrics.increment("generation_readiness_comparison_mismatch");
            metrics.increment(reason.metricName());
        }
        try {
            comparisonPort.append(new ReadinessComparisonRecord(java.util.UUID.randomUUID().toString(),
                    operationId, sourceType, legacy.ready(), common.releaseReady(), matched,
                    reason == null ? null : reason.name(), legacy.failedGateNames(),
                    common.failedGateNames(), common.missingGateNames(), java.time.Instant.now()));
        } catch (RuntimeException exception) {
            metrics.increment("generation_readiness_comparison_persistence_failure");
            log.error("[generation-readiness-compare] 영속화 실패: operationId={}", operationId, exception);
        }
        log.info("[generation-readiness-compare] operationId={}, sourceType={}, legacyReady={}, "
                        + "commonReady={}, matched={}, mismatchReason={}, legacyFailed={}, commonFailed={}, commonMissing={}",
                operationId, sourceType, legacy.ready(), common.releaseReady(), matched,
                reason, legacy.failedGateNames(), common.failedGateNames(), common.missingGateNames());
    }

    public ComparisonMetrics metrics() {
        var persisted = comparisonPort.summary();
        if (persisted.isPresent()) {
            var summary = persisted.get();
            return new ComparisonMetrics(summary.total(), summary.mismatch(),
                    summary.total() - summary.mismatch(), completeReasons(summary.mismatchReasons()));
        }
        long total = metrics.count("generation_readiness_comparison_total");
        long mismatch = metrics.count("generation_readiness_comparison_mismatch");
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (MismatchReason reason : MismatchReason.values()) reasons.put(reason.name(), metrics.count(reason.metricName()));
        return new ComparisonMetrics(total, mismatch, total - mismatch, reasons);
    }

    public ReadinessComparisonReport report(Instant from, Instant to) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("READINESS_COMPARISON_PERIOD_INVALID");
        }
        if (java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(366)) > 0) {
            throw new IllegalArgumentException("READINESS_COMPARISON_PERIOD_TOO_LARGE");
        }
        return comparisonPort.report(from, to)
                .orElseGet(() -> new ReadinessComparisonReport(from, to, 0, 0, 0.0,
                        Map.of(), Map.of(), Map.of()));
    }

    private Map<String, Long> completeReasons(Map<String, Long> persisted) {
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (MismatchReason reason : MismatchReason.values()) reasons.put(reason.name(), persisted.getOrDefault(reason.name(), 0L));
        return reasons;
    }

    private MismatchReason classify(PipelineReleaseReadiness.Readiness legacy, ReleaseReadiness common) {
        if (!common.missingGateNames().isEmpty()) return MismatchReason.COMMON_EVIDENCE_MISSING;
        if (legacy.ready() && !common.releaseReady()) return MismatchReason.COMMON_STRICTER;
        if (!legacy.ready() && common.releaseReady()) return MismatchReason.LEGACY_STRICTER;
        return MismatchReason.GATE_DETAIL_DIFFERENCE;
    }

    public enum MismatchReason {
        COMMON_EVIDENCE_MISSING,
        COMMON_STRICTER,
        LEGACY_STRICTER,
        GATE_DETAIL_DIFFERENCE;

        String metricName() {
            return "generation_readiness_mismatch_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record ComparisonMetrics(long total, long mismatch, long matched, Map<String, Long> mismatchReasons) {
        public ComparisonMetrics {
            mismatchReasons = Map.copyOf(mismatchReasons);
        }
    }
}
