package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.service.generation.CrudGenerationOperationIdFactory;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 신규 CRUD 실행의 실제 Renderer·Verifier 결과만 공통 검증 증적으로 기록한다. */
@Component
public class CrudValidationEvidenceRecorder implements GenerationVerificationObserver {

    private final ValidationEvidencePort evidencePort;

    public CrudValidationEvidenceRecorder(ValidationEvidencePort evidencePort) {
        this.evidencePort = evidencePort;
    }

    @Override
    public void onCompleted(GenerationProcessingContext context,
                            GenerationVerifierRunner.VerificationRunResult result) {
        if (!"crud".equalsIgnoreCase(context.context().feature())) {
            return;
        }
        String operationId = CrudGenerationOperationIdFactory.forScreen(
                context.context().outputPath(), context.context().tableName(), context.context().viewType());

        boolean renderFailed = context.renderedPlan() == null || context.renderedPlan().files().stream()
                .anyMatch(file -> !file.rendered());
        append(operationId, ValidationEvidence.GateType.RENDER,
                renderFailed ? ValidationEvidence.Status.FAILED : ValidationEvidence.Status.PASSED,
                renderFailed ? ValidationEvidence.Severity.BLOCK : ValidationEvidence.Severity.INFO,
                List.of("renderer"));

        result.outcomes().forEach(outcome -> {
            ValidationEvidence.GateType gateType = gateType(outcome.verifierId());
            if (gateType == null) {
                return;
            }
            boolean summaryFailure = outcome.result().summaryFragment() != null
                    && outcome.result().summaryFragment().contains("검증 실패");
            boolean failed = summaryFailure || !outcome.result().failures().isEmpty();
            append(operationId, gateType,
                    failed ? ValidationEvidence.Status.FAILED : ValidationEvidence.Status.PASSED,
                    failed ? ValidationEvidence.Severity.BLOCK : ValidationEvidence.Severity.INFO,
                    List.of(outcome.verifierId()));
        });
    }

    private ValidationEvidence.GateType gateType(String verifierId) {
        return switch (verifierId) {
            case "codeDirectoryVerifier" -> ValidationEvidence.GateType.BUILD;
            case "commonGeneratedContractVerifier" -> ValidationEvidence.GateType.BINDING;
            default -> null;
        };
    }

    private void append(String operationId, ValidationEvidence.GateType gateType,
                        ValidationEvidence.Status status, ValidationEvidence.Severity severity,
                        List<String> outputRefs) {
        evidencePort.append(new ValidationEvidence(UUID.randomUUID().toString(), operationId, gateType,
                status, severity, List.of(), outputRefs, null, null, "generation-pipeline-v1", Instant.now()));
    }
}
