package com.krdevops.springai.service.controlplane;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.artifact.Artifact;
import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.controlplane.ApprovalMode;
import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.model.thymeleaf.GateSeverity;
import com.krdevops.springai.model.thymeleaf.ThymeleafOperationSnapshot;
import com.krdevops.springai.model.thymeleaf.ValidationGateResult;
import com.krdevops.springai.model.thymeleaf.ValidationGateType;
import com.krdevops.springai.model.thymeleaf.ValidationReport;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.artifact.ArtifactService;
import com.krdevops.springai.service.thymeleaf.ThymeleafOperationStore;
import com.krdevops.springai.service.thymeleaf.ValidationGateExecutor;
import com.krdevops.springai.service.operation.NoopOperationEventPort;
import com.krdevops.springai.service.operation.OperationEventPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Thymeleaf Operation과 연결된 검증 Report를 공통 읽기 모델로 변환한다. */
@Component
public class ThymeleafWorkflowAdapter implements GenerationOperationAdapter {

    private final ThymeleafOperationStore operationStore;
    private final ArtifactService artifactService;
    private final ValidationGateExecutor validationGateExecutor;
    private final OperationStatusNormalizer statusNormalizer;
    private final ObjectMapper objectMapper;
    private final OperationEventPort eventPort;

    @Autowired
    public ThymeleafWorkflowAdapter(ThymeleafOperationStore operationStore, ArtifactService artifactService,
            ValidationGateExecutor validationGateExecutor, OperationStatusNormalizer statusNormalizer,
            ObjectMapper objectMapper, OperationEventPort eventPort) {
        this.operationStore = operationStore;
        this.artifactService = artifactService;
        this.validationGateExecutor = validationGateExecutor;
        this.statusNormalizer = statusNormalizer;
        this.objectMapper = objectMapper;
        this.eventPort = eventPort;
    }

    public ThymeleafWorkflowAdapter(ThymeleafOperationStore operationStore, ArtifactService artifactService,
            ValidationGateExecutor validationGateExecutor, OperationStatusNormalizer statusNormalizer,
            ObjectMapper objectMapper) {
        this(operationStore, artifactService, validationGateExecutor, statusNormalizer, objectMapper,
                new NoopOperationEventPort());
    }

    @Override
    public GenerationSourceType sourceType() {
        return GenerationSourceType.THYMELEAF_MIGRATION;
    }

    @Override
    public Optional<GenerationOperation> find(String operationId) {
        return operationStore.findLatest(operationId).map(snapshot -> project(snapshot, evidence(operationId)));
    }

    @Override
    public List<ValidationEvidence> evidence(String operationId) {
        List<ValidationEvidence> projected = new ArrayList<>();
        for (Artifact artifact : artifactService.findByOperation(operationId)) {
            if (!"THYMELEAF_VALIDATION_REPORT".equals(artifact.artifactType())) continue;
            artifactService.read(artifact).ifPresent(content -> addReport(projected, operationId, artifact, content));
        }
        return List.copyOf(projected);
    }

    private GenerationOperation project(ThymeleafOperationSnapshot snapshot,
                                        List<ValidationEvidence> evidence) {
        var operation = snapshot.operation();
        String sourceStatus = operation.status().name();
        String auditHash = ContentHashes.sha256Hex(json(snapshot));
        String screenId = snapshot.bindingContract() == null ? null : snapshot.bindingContract().screenId();
        var latestEvent = eventPort.findByOperation(operation.operationId()).stream()
                .max(java.util.Comparator.comparing(com.krdevops.springai.model.operation.OperationEvent::occurredAt))
                .orElse(null);
        return new GenerationOperation(operation.operationId(), GenerationSourceType.THYMELEAF_MIGRATION,
                operation.operationId(), "AI_THYMELEAF_PROJECT_OPERATION",
                operation.operationId() + "/" + snapshot.revision(), sourceStatus,
                snapshot.projectRoot(), screenId, null, snapshot.designRevision(), snapshot.revision(),
                ApprovalMode.EXPLICIT_HASH_APPROVAL, sourceStatus, ProjectWritePolicy.ATOMIC_APPROVED,
                statusNormalizer.normalize(sourceStatus), operation.targetFiles(), operation.conflictingFiles(),
                evidence.isEmpty() ? EvidenceRecordingStatus.NOT_RECORDED : EvidenceRecordingStatus.RECORDED,
                EvidenceRecordingStatus.RECORDED,
                latestEvent == null ? "system" : latestEvent.actor(),
                latestEvent == null || latestEvent.callerType() == null ? "UNKNOWN" : latestEvent.callerType(),
                latestEvent == null || latestEvent.environment() == null ? "UNKNOWN" : latestEvent.environment(),
                operation.createdAt() == null ? null : operation.createdAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                operation.appliedAt() == null ? null : operation.appliedAt().atZone(java.time.ZoneId.systemDefault()).toInstant(),
                auditHash);
    }

    private void addReport(List<ValidationEvidence> target, String operationId,
                           Artifact artifact, byte[] content) {
        try {
            ValidationReport report = objectMapper.readValue(content, ValidationReport.class);
            for (ValidationGateResult gate : report.results()) {
                ValidationEvidence.GateType gateType = map(gate.gateType());
                if (gateType == null) continue;
                GateSeverity gateSeverity = validationGateExecutor.severityOf(gate.gateType());
                target.add(new ValidationEvidence(
                        artifact.artifactId() + ":" + gate.gateType().name(), operationId, gateType,
                        gate.passed() ? ValidationEvidence.Status.PASSED : ValidationEvidence.Status.FAILED,
                        gateSeverity == GateSeverity.BLOCK
                                ? ValidationEvidence.Severity.BLOCK : ValidationEvidence.Severity.WARN,
                        List.of(report.inputHash()), gate.issues(), artifact.contentHash(), artifact.sourceRevision(),
                        "thymeleaf-validation-v1", gate.timestamp()));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Thymeleaf 검증 Report 변환 실패: " + artifact.artifactId(), exception);
        }
    }

    private ValidationEvidence.GateType map(ValidationGateType type) {
        return switch (type) {
            case BINDING_VALIDATION, ROUTE_PARITY -> ValidationEvidence.GateType.BINDING;
            case BUILD_VALIDATION -> ValidationEvidence.GateType.BUILD;
            case THYMELEAF_PARSE, TEMPLATE_ENGINE_RENDER, BROWSER_RENDER -> ValidationEvidence.GateType.RENDER;
            case ACCESSIBILITY -> ValidationEvidence.GateType.ACCESSIBILITY;
            case VISUAL_PARITY -> ValidationEvidence.GateType.VISUAL;
            case OVERFLOW_CHECK -> ValidationEvidence.GateType.INTERACTION;
        };
    }

    private byte[] json(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Thymeleaf Operation 감사 Hash 생성 실패", exception);
        }
    }
}
