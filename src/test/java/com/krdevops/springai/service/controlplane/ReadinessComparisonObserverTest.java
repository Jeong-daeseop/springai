package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.model.controlplane.ReadinessComparisonSummary;
import com.krdevops.springai.model.controlplane.ReadinessComparisonReport;
import com.krdevops.springai.service.e2e.PipelineReleaseReadiness;
import com.krdevops.springai.service.observability.PipelineMetricsCollector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ReadinessComparisonObserverTest {
    @Test
    void 기존_판정과_공통_판정의_일치와_불일치를_계수로_집계한다() {
        var observer = new ReadinessComparisonObserver(new PipelineMetricsCollector());
        var ready = new PipelineReleaseReadiness.Readiness(true, java.util.Map.of("BUILD", true));
        observer.observe("op-1", GenerationSourceType.CRUD, ready, common(true));
        observer.observe("op-2", GenerationSourceType.THYMELEAF_MIGRATION, ready, common(false));
        var result = observer.metrics();
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.mismatch()).isEqualTo(1);
        assertThat(result.matched()).isEqualTo(1);
        assertThat(result.mismatchReasons()).containsEntry("COMMON_STRICTER", 1L)
                .containsEntry("COMMON_EVIDENCE_MISSING", 0L)
                .containsEntry("LEGACY_STRICTER", 0L);
    }

    @Test
    void 공통_증적_누락을_일반적인_엄격도_차이보다_우선_분류한다() {
        var observer = new ReadinessComparisonObserver(new PipelineMetricsCollector());
        var legacy = new PipelineReleaseReadiness.Readiness(true, java.util.Map.of("BUILD", true));
        var common = new ReleaseReadiness("op", GenerationSourceType.CRUD, false,
                EvidenceRecordingStatus.NOT_RECORDED, List.of(), List.of("BUILD"), "hash");

        observer.observe("op", GenerationSourceType.CRUD, legacy, common);

        assertThat(observer.metrics().mismatchReasons()).containsEntry("COMMON_EVIDENCE_MISSING", 1L);
    }

    @Test
    void 영속_저장소가_있으면_비교를_기록하고_재시작에_안전한_누적값을_조회한다() {
        ReadinessComparisonPort port = mock(ReadinessComparisonPort.class);
        given(port.summary()).willReturn(java.util.Optional.of(
                new ReadinessComparisonSummary(30, 4, java.util.Map.of("COMMON_STRICTER", 4L))));
        var observer = new ReadinessComparisonObserver(new PipelineMetricsCollector(), port);

        observer.observe("op", GenerationSourceType.CRUD,
                new PipelineReleaseReadiness.Readiness(true, java.util.Map.of("BUILD", true)), common(false));

        verify(port).append(org.mockito.ArgumentMatchers.argThat(record ->
                record.operationId().equals("op") && "COMMON_STRICTER".equals(record.mismatchReason())));
        assertThat(observer.metrics().total()).isEqualTo(30);
        assertThat(observer.metrics().mismatchReasons()).containsEntry("COMMON_STRICTER", 4L)
                .containsEntry("LEGACY_STRICTER", 0L);
    }

    @Test
    void 기간별_보고서는_영속_저장소에_위임하고_최대_366일로_제한한다() {
        ReadinessComparisonPort port = mock(ReadinessComparisonPort.class);
        java.time.Instant to = java.time.Instant.parse("2026-09-24T00:00:00Z");
        java.time.Instant from = to.minus(java.time.Duration.ofDays(30));
        var expected = new ReadinessComparisonReport(from, to, 100, 3, 0.03,
                java.util.Map.of("COMMON_STRICTER", 3L), java.util.Map.of("CRUD", 100L), java.util.Map.of());
        given(port.report(from, to)).willReturn(java.util.Optional.of(expected));
        var observer = new ReadinessComparisonObserver(new PipelineMetricsCollector(), port);

        assertThat(observer.report(from, to)).isEqualTo(expected);
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                observer.report(from, to.plus(java.time.Duration.ofDays(367))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PERIOD_TOO_LARGE");
    }

    private ReleaseReadiness common(boolean ready) {
        return new ReleaseReadiness("op", GenerationSourceType.CRUD, ready,
                EvidenceRecordingStatus.RECORDED, List.of(), List.of(), "hash");
    }
}
