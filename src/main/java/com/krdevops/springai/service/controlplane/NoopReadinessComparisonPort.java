package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.ReadinessComparisonRecord;
import com.krdevops.springai.model.controlplane.ReadinessComparisonSummary;
import com.krdevops.springai.model.controlplane.ReadinessComparisonReport;

import java.time.Instant;
import java.util.Optional;

final class NoopReadinessComparisonPort implements ReadinessComparisonPort {
    @Override public void append(ReadinessComparisonRecord record) { }
    @Override public Optional<ReadinessComparisonSummary> summary() { return Optional.empty(); }
    @Override public Optional<ReadinessComparisonReport> report(Instant from, Instant to) { return Optional.empty(); }
}
