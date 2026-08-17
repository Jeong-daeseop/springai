package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.GenerationIssue;

import java.time.Instant;
import java.util.List;

/**
 * R6-050: 한 단계의 실행 증적. 완료 단계는 입력·출력 Hash, 계약 버전, 시간, 산출물과 Issue를
 * 함께 보존한다. FAILED/REVIEW_REQUIRED 뒤 단계는 실행하지 않고 SKIPPED로 기록한다.
 */
public record ThymeleafGenerationStageExecution(
        ThymeleafGenerationStage stage,
        ThymeleafGenerationStageStatus status,
        String inputHash,
        String outputHash,
        String contractVersion,
        Instant startedAt,
        Instant completedAt,
        List<ArtifactRef> artifactRefs,
        List<GenerationIssue> issues,
        ThymeleafGenerationStage blockedByStage,
        int attempt
) {
    public ThymeleafGenerationStageExecution {
        if (stage == null || status == null) {
            throw new IllegalArgumentException("stage와 status는 필수입니다.");
        }
        if (contractVersion == null || contractVersion.isBlank()) {
            throw new IllegalArgumentException("contractVersion은 필수입니다.");
        }
        boolean notExecuted = status == ThymeleafGenerationStageStatus.PENDING
                || status == ThymeleafGenerationStageStatus.SKIPPED;
        if (attempt < 0 || (notExecuted && attempt != 0) || (!notExecuted && attempt == 0)) {
            throw new IllegalArgumentException("PENDING/SKIPPED attempt는 0, 실행된 단계는 1 이상이어야 합니다.");
        }
        artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
        issues = issues == null ? List.of() : List.copyOf(issues);

        if (status == ThymeleafGenerationStageStatus.PENDING) {
            requireNull(inputHash, "PENDING inputHash");
            requireNull(outputHash, "PENDING outputHash");
            requireNull(startedAt, "PENDING startedAt");
            requireNull(completedAt, "PENDING completedAt");
        } else if (status == ThymeleafGenerationStageStatus.SKIPPED) {
            if (blockedByStage == null || blockedByStage.order() >= stage.order()) {
                throw new IllegalArgumentException("SKIPPED는 앞선 차단 단계를 참조해야 합니다.");
            }
            requireNull(inputHash, "SKIPPED inputHash");
            requireNull(outputHash, "SKIPPED outputHash");
            requireNull(startedAt, "SKIPPED startedAt");
            requireNull(completedAt, "SKIPPED completedAt");
        } else {
            ContentHashes.requireValid(inputHash);
            if (startedAt == null) {
                throw new IllegalArgumentException("실행 단계의 startedAt은 필수입니다.");
            }
            if (status.terminal() && completedAt == null) {
                throw new IllegalArgumentException("종료 단계의 completedAt은 필수입니다.");
            }
            if (status == ThymeleafGenerationStageStatus.SUCCEEDED) {
                ContentHashes.requireValid(outputHash);
            } else if (outputHash != null) {
                ContentHashes.requireValid(outputHash);
            }
            if (completedAt != null && completedAt.isBefore(startedAt)) {
                throw new IllegalArgumentException("completedAt은 startedAt보다 빠를 수 없습니다.");
            }
        }

        boolean fatal = issues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.FATAL);
        boolean error = issues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.ERROR);
        if (status == ThymeleafGenerationStageStatus.FAILED && !fatal) {
            throw new IllegalArgumentException("FAILED 단계에는 최소 하나의 FATAL Issue가 필요합니다.");
        }
        if (status == ThymeleafGenerationStageStatus.REVIEW_REQUIRED && (fatal || !error)) {
            throw new IllegalArgumentException("REVIEW_REQUIRED에는 FATAL 없이 ERROR Issue가 필요합니다.");
        }
        if (status == ThymeleafGenerationStageStatus.SUCCEEDED && (fatal || error)) {
            throw new IllegalArgumentException("SUCCEEDED에는 FATAL/ERROR Issue가 포함될 수 없습니다.");
        }
    }

    private static void requireNull(Object value, String name) {
        if (value != null) {
            throw new IllegalArgumentException(name + "는 null이어야 합니다.");
        }
    }
}
