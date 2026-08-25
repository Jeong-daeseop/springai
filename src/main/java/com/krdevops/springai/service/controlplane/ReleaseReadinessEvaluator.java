package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Component
public class ReleaseReadinessEvaluator {

    private static final Set<ValidationEvidence.GateType> REQUIRED = EnumSet.of(
            ValidationEvidence.GateType.BINDING,
            ValidationEvidence.GateType.BUILD,
            ValidationEvidence.GateType.RENDER);

    public ReleaseReadiness evaluate(GenerationOperation operation, List<ValidationEvidence> evidence) {
        List<ValidationEvidence> values = evidence == null ? List.of() : evidence;
        List<String> failed = values.stream()
                .filter(item -> item.status() == ValidationEvidence.Status.FAILED
                        && item.severity() == ValidationEvidence.Severity.BLOCK)
                .map(item -> item.gateType().name()).distinct().sorted().toList();
        Set<ValidationEvidence.GateType> recorded = EnumSet.noneOf(ValidationEvidence.GateType.class);
        values.stream()
                .filter(item -> item.status() == ValidationEvidence.Status.PASSED
                        || item.status() == ValidationEvidence.Status.FAILED)
                .map(ValidationEvidence::gateType).forEach(recorded::add);
        List<String> missing = REQUIRED.stream().filter(gate -> !recorded.contains(gate))
                .map(Enum::name).sorted().toList();
        boolean applied = operation.status() == GenerationOperationStatus.APPLIED
                || operation.status() == GenerationOperationStatus.VALIDATED;
        boolean ready = applied && failed.isEmpty() && missing.isEmpty();
        EvidenceRecordingStatus recordingStatus = values.isEmpty()
                ? EvidenceRecordingStatus.NOT_RECORDED : EvidenceRecordingStatus.RECORDED;
        return new ReleaseReadiness(operation.operationId(), operation.sourceType(), ready,
                recordingStatus, failed, missing, operation.auditSnapshotHash());
    }
}
