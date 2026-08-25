package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.GenerationAuditRecord;

import java.util.List;

public interface CrudGenerationAuditPort {
    void append(GenerationAuditRecord record);
    List<GenerationAuditRecord> findAudits(String operationId);

    static CrudGenerationAuditPort none() {
        return new CrudGenerationAuditPort() {
            @Override public void append(GenerationAuditRecord record) { }
            @Override public List<GenerationAuditRecord> findAudits(String operationId) { return List.of(); }
        };
    }
}
