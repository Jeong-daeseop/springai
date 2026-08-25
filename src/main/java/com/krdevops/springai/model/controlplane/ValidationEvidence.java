package com.krdevops.springai.model.controlplane;

import java.time.Instant;
import java.util.List;

public record ValidationEvidence(
        String evidenceId,
        String operationId,
        GateType gateType,
        Status status,
        Severity severity,
        List<String> inputRefs,
        List<String> outputRefs,
        String contentHash,
        String sourceRevision,
        String validatorVersion,
        Instant createdAt) {

    public ValidationEvidence {
        inputRefs = inputRefs == null ? List.of() : List.copyOf(inputRefs);
        outputRefs = outputRefs == null ? List.of() : List.copyOf(outputRefs);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public enum GateType { BINDING, BUILD, RENDER, ACCESSIBILITY, VISUAL, INTERACTION }
    public enum Status { PASSED, FAILED, SKIPPED, INCOMPLETE }
    public enum Severity { BLOCK, WARN, INFO }
}
