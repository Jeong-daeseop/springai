package com.krdevops.springai.model.controlplane;

import java.util.List;

public record ReleaseReadiness(
        String operationId,
        GenerationSourceType sourceType,
        boolean releaseReady,
        EvidenceRecordingStatus validationEvidenceStatus,
        List<String> failedGateNames,
        List<String> missingGateNames,
        String auditSnapshotHash) {

    public ReleaseReadiness {
        failedGateNames = failedGateNames == null ? List.of() : List.copyOf(failedGateNames);
        missingGateNames = missingGateNames == null ? List.of() : List.copyOf(missingGateNames);
    }
}
