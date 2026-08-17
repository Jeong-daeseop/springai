package com.krdevops.springai.model.contract;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.thymeleaf.ProjectOperationStatus;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationPipelineContract;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStage;
import com.krdevops.springai.model.thymeleaf.ThymeleafGenerationStageExecution;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * R6-061: 10단계 Thymeleaf Generator와 Preview→Approve→Apply→Validate 상태를 함께 보존하는
 * 불변 보고서. Operation Snapshot에 포함되어 MySQL revision 저장소와 동일한 재시작 복구·CAS
 * 보장을 받는다.
 */
public record ThymeleafGenerationReport(
        String generationId,
        String operationId,
        String requestHash,
        String projectFingerprint,
        String sourceRevision,
        String targetRuntimeProfile,
        Map<String, String> contractVersions,
        List<ThymeleafGenerationStageExecution> stages,
        List<GeneratedFile> generatedFiles,
        ProjectOperationStatus finalStatus,
        Instant createdAt,
        Instant updatedAt
) {
    public ThymeleafGenerationReport {
        requireText(generationId, "generationId");
        requireText(operationId, "operationId");
        ContentHashes.requireValid(requestHash);
        requireText(projectFingerprint, "projectFingerprint");
        requireText(sourceRevision, "sourceRevision");
        targetRuntimeProfile = targetRuntimeProfile == null || targetRuntimeProfile.isBlank()
                ? "SPRING_BOOT_THYMELEAF" : targetRuntimeProfile;
        contractVersions = contractVersions == null ? Map.of() : Map.copyOf(contractVersions);
        stages = stages == null ? List.of() : List.copyOf(stages);
        generatedFiles = generatedFiles == null ? List.of() : List.copyOf(generatedFiles);
        if (finalStatus == null || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("finalStatus/createdAt/updatedAt은 필수입니다.");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt은 createdAt보다 빠를 수 없습니다.");
        }
        validateStages(stages);
        if (!ThymeleafGenerationPipelineContract.CONTRACT_VERSION.equals(
                contractVersions.get("pipeline"))) {
            throw new IllegalArgumentException("pipeline contractVersion이 누락되거나 다릅니다.");
        }
    }

    private static void validateStages(List<ThymeleafGenerationStageExecution> stages) {
        if (stages.size() != ThymeleafGenerationPipelineContract.stages().size()) {
            throw new IllegalArgumentException("보고서는 정확히 10개 단계 증적을 포함해야 합니다.");
        }
        HashSet<ThymeleafGenerationStage> unique = new HashSet<>();
        for (int index = 0; index < stages.size(); index++) {
            ThymeleafGenerationStageExecution execution = stages.get(index);
            ThymeleafGenerationStage expected = ThymeleafGenerationPipelineContract.stages().get(index);
            if (execution.stage() != expected || !unique.add(execution.stage())) {
                throw new IllegalArgumentException("보고서 단계는 중복 없이 계약 순서와 일치해야 합니다.");
            }
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "는 필수입니다.");
        }
    }

    public record GeneratedFile(String relativePath, String contentHash, long size) {
        public GeneratedFile {
            requireText(relativePath, "relativePath");
            ContentHashes.requireValid(contentHash);
            if (size < 0) {
                throw new IllegalArgumentException("size는 음수일 수 없습니다.");
            }
        }
    }
}
