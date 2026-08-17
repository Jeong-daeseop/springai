package com.krdevops.springai.service.thymeleaf;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.ThymeleafGenerationReport;
import com.krdevops.springai.model.thymeleaf.LegacySourceManifest;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafBindingContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationPipelineContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStage;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageExecution;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafProjectOperation;
import com.krdevops.springai.service.contract.OperationHashFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** R6-061: 10단계 실행 증적을 Operation 생명주기와 결합하는 불변 Report 조립기. */
@Service
@RequiredArgsConstructor
public class ThymeleafGenerationReportService {

    private final OperationHashFactory hashFactory;

    public ThymeleafGenerationReport previewReport(
            ThymeleafProjectOperation operation,
            String previewHash,
            String projectRoot,
            String designRevision,
            LegacySourceManifest manifest,
            ThymeleafBindingContract bindingContract,
            Map<String, String> generatedFiles) {
        if (bindingContract == null) {
            return null;
        }
        Instant now = Instant.now();
        List<ThymeleafGenerationStageExecution> executions = new ArrayList<>();
        String previousOutput = null;
        for (ThymeleafGenerationStage stage : ThymeleafGenerationPipelineContract.stages()) {
            if (stage == ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION) {
                executions.add(pending(stage));
                continue;
            }
            Map<String, Object> basis = stageBasis(
                    stage, projectRoot, designRevision, manifest, bindingContract, generatedFiles);
            String inputHash = ThymeleafGenerationPipelineContract.inputHash(stage, previousOutput, basis);
            String outputHash = hashFactory.canonicalHash(Map.of(
                    "stage", stage.name(), "inputHash", inputHash, "evidence", basis));
            executions.add(new ThymeleafGenerationStageExecution(
                    stage, ThymeleafGenerationStageStatus.SUCCEEDED,
                    inputHash, outputHash, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                    now, now, List.of(), List.of(), null, 1));
            previousOutput = outputHash;
        }

        List<ThymeleafGenerationReport.GeneratedFile> files = generatedFiles.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ThymeleafGenerationReport.GeneratedFile(
                        entry.getKey(), hashFactory.sha256Hex(entry.getValue().getBytes(StandardCharsets.UTF_8)),
                        entry.getValue().getBytes(StandardCharsets.UTF_8).length))
                .toList();
        String projectFingerprint = manifest != null && manifest.tracked()
                ? manifest.fingerprint()
                : hashFactory.sha256Hex(projectRoot.getBytes(StandardCharsets.UTF_8));
        String sourceRevision = manifest != null && manifest.tracked()
                ? manifest.fingerprint() : designRevision;
        return new ThymeleafGenerationReport(
                "thymeleaf-gen-" + operation.operationId(), operation.operationId(), previewHash,
                projectFingerprint, sourceRevision, "SPRING_BOOT_THYMELEAF",
                Map.of("pipeline", ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                        "binding", "thymeleaf-binding-contract-v1"),
                executions, files, ProjectOperationStatus.PREVIEW_READY, now, now);
    }

    /** Operation revision마다 Report 최종 상태를 함께 전이하고, 검증 결과는 10단계에 기록한다. */
    public ThymeleafGenerationReport transition(
            ThymeleafGenerationReport previous,
            ProjectOperationStatus previousStatus,
            ThymeleafProjectOperation current) {
        if (previous == null) {
            return null;
        }
        List<ThymeleafGenerationStageExecution> stages = new ArrayList<>(previous.stages());
        if (previousStatus == ProjectOperationStatus.APPLIED
                && (current.status() == ProjectOperationStatus.VALIDATED
                || current.status() == ProjectOperationStatus.FAILED)) {
            stages.set(9, validationExecution(stages.get(8), current));
        }
        return new ThymeleafGenerationReport(
                previous.generationId(), previous.operationId(), previous.requestHash(),
                previous.projectFingerprint(), previous.sourceRevision(), previous.targetRuntimeProfile(),
                previous.contractVersions(), stages, previous.generatedFiles(), current.status(),
                previous.createdAt(), Instant.now());
    }

    private ThymeleafGenerationStageExecution validationExecution(
            ThymeleafGenerationStageExecution responsive, ThymeleafProjectOperation operation) {
        Instant now = Instant.now();
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("operationId", operation.operationId());
        basis.put("targetFiles", operation.targetFiles());
        basis.put("validationErrors", operation.validationErrors());
        String inputHash = ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION,
                responsive.outputHash(), basis);
        if (operation.status() == ProjectOperationStatus.VALIDATED) {
            String outputHash = hashFactory.canonicalHash(Map.of(
                    "status", operation.status().name(), "inputHash", inputHash));
            return new ThymeleafGenerationStageExecution(
                    ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION,
                    ThymeleafGenerationStageStatus.SUCCEEDED,
                    inputHash, outputHash, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                    now, now, List.of(), List.of(), null, 1);
        }
        GenerationIssue issue = new GenerationIssue(
                "THYMELEAF_BUILD_RENDER_PARITY_FAILED", GenerationIssue.Severity.FATAL,
                ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION.name(), null,
                operation.validationErrors().isEmpty()
                        ? "빌드·렌더·Parity 검증에 실패했습니다."
                        : String.join("; ", operation.validationErrors()),
                "검증 오류를 수정한 뒤 환경 복구 재시도 정책으로 다시 검증하세요.");
        return new ThymeleafGenerationStageExecution(
                ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION,
                ThymeleafGenerationStageStatus.FAILED,
                inputHash, null, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                now, now, List.of(), List.of(issue), null, 1);
    }

    private ThymeleafGenerationStageExecution pending(ThymeleafGenerationStage stage) {
        return new ThymeleafGenerationStageExecution(
                stage, ThymeleafGenerationStageStatus.PENDING,
                null, null, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                null, null, List.of(), List.of(), null, 0);
    }

    private Map<String, Object> stageBasis(
            ThymeleafGenerationStage stage, String projectRoot, String designRevision,
            LegacySourceManifest manifest, ThymeleafBindingContract bindingContract,
            Map<String, String> generatedFiles) {
        Map<String, Object> basis = new LinkedHashMap<>();
        basis.put("stage", stage.name());
        basis.put("projectRoot", projectRoot);
        basis.put("designRevision", designRevision);
        basis.put("sourceFingerprint", manifest != null && manifest.tracked()
                ? manifest.fingerprint() : "UNTRACKED");
        basis.put("screenId", bindingContract.screenId());
        basis.put("screenRole", bindingContract.screenRole().name());
        basis.put("bindingContract", bindingContractEvidence(bindingContract));
        if (stage.order() >= ThymeleafGenerationStage.HTML_SKELETON.order()) {
            basis.put("generatedFiles", generatedFiles);
        }
        return basis;
    }

    /** createdAt/capturedAt 같은 관측 시각은 동일 입력 재실행 Hash에서 제외한다. */
    private Map<String, Object> bindingContractEvidence(ThymeleafBindingContract contract) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("screenId", contract.screenId());
        evidence.put("screenRole", contract.screenRole());
        evidence.put("route", contract.route());
        evidence.put("fields", contract.fields());
        evidence.put("displayFieldNames", contract.displayFieldNames());
        evidence.put("primaryDisplayAttributeName", contract.primaryDisplayAttributeName());
        evidence.put("secondaryDisplayFieldNames", contract.secondaryDisplayFieldNames());
        evidence.put("secondaryDisplayAttributeName", contract.secondaryDisplayAttributeName());
        evidence.put("modelAttributesResolved", contract.modelAttributesResolved());
        evidence.put("modelAttributesUnresolved", contract.modelAttributesUnresolved());
        evidence.put("status", contract.status());
        evidence.put("issues", contract.issues());
        evidence.put("sourceRevisionToken", contract.sourceRevision() == null
                ? null : contract.sourceRevision().revisionToken());
        return evidence;
    }
}
