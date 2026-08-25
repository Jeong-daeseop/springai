package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.ReadinessComparisonRecord;
import com.krdevops.springai.model.controlplane.ReadinessComparisonSummary;
import com.krdevops.springai.model.controlplane.ReadinessComparisonReport;

import java.time.Instant;
import java.util.Optional;

public interface ReadinessComparisonPort {
    void append(ReadinessComparisonRecord record);
    Optional<ReadinessComparisonSummary> summary();
    Optional<ReadinessComparisonReport> report(Instant from, Instant to);
}
