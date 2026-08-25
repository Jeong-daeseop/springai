package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.ValidationEvidence;

import java.util.List;

public interface ValidationEvidencePort {
    void append(ValidationEvidence evidence);
    List<ValidationEvidence> findEvidence(String operationId);

    static ValidationEvidencePort none() {
        return new ValidationEvidencePort() {
            @Override public void append(ValidationEvidence evidence) { }
            @Override public List<ValidationEvidence> findEvidence(String operationId) { return List.of(); }
        };
    }
}
