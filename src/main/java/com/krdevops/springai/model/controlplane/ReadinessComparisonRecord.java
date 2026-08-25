package com.krdevops.springai.model.controlplane;

import java.time.Instant;
import java.util.List;

/** 기존 Gate 판정과 공통 판정의 개인정보 없는 장기 관측 기록. */
public record ReadinessComparisonRecord(
        String comparisonId,
        String operationId,
        GenerationSourceType sourceType,
        boolean legacyReady,
        boolean commonReady,
        boolean matched,
        String mismatchReason,
        List<String> legacyFailedGateNames,
        List<String> commonFailedGateNames,
        List<String> commonMissingGateNames,
        Instant createdAt) {

    public ReadinessComparisonRecord {
        legacyFailedGateNames = legacyFailedGateNames == null ? List.of() : List.copyOf(legacyFailedGateNames);
        commonFailedGateNames = commonFailedGateNames == null ? List.of() : List.copyOf(commonFailedGateNames);
        commonMissingGateNames = commonMissingGateNames == null ? List.of() : List.copyOf(commonMissingGateNames);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
