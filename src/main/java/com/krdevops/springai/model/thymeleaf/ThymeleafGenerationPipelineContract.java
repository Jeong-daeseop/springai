package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.service.common.CanonicalJsonHasher;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** R6-050 고정 10단계의 판정·Hash·재시도 정책을 제공하는 실행 독립 계약. */
public final class ThymeleafGenerationPipelineContract {

    public static final String CONTRACT_VERSION = "thymeleaf-generation-pipeline-v1";
    private static final List<ThymeleafGenerationStage> STAGES = List.copyOf(
            Arrays.asList(ThymeleafGenerationStage.values()));

    private ThymeleafGenerationPipelineContract() {
    }

    public static List<ThymeleafGenerationStage> stages() {
        return STAGES;
    }

    /** FATAL은 즉시 실패, ERROR는 사람 검토, WARNING만 있으면 성공으로 판정한다. */
    public static ThymeleafGenerationStageStatus terminalStatus(List<GenerationIssue> issues) {
        List<GenerationIssue> safeIssues = issues == null ? List.of() : issues;
        if (safeIssues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.FATAL)) {
            return ThymeleafGenerationStageStatus.FAILED;
        }
        if (safeIssues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.ERROR)) {
            return ThymeleafGenerationStageStatus.REVIEW_REQUIRED;
        }
        return ThymeleafGenerationStageStatus.SUCCEEDED;
    }

    public static boolean blocksFollowingStages(ThymeleafGenerationStageStatus status) {
        return status == ThymeleafGenerationStageStatus.FAILED
                || status == ThymeleafGenerationStageStatus.REVIEW_REQUIRED;
    }

    /**
     * 단계 입력의 canonical JSON, 단계 ID, 계약 버전과 직전 output hash를 함께 묶는다.
     * 따라서 같은 입력·버전·선행 산출물은 같은 Hash를 만들고 어느 하나가 바뀌면 달라진다.
     */
    public static String inputHash(
            ThymeleafGenerationStage stage, String previousOutputHash, Object canonicalInput) {
        if (stage == null || canonicalInput == null) {
            throw new IllegalArgumentException("stage와 canonicalInput은 필수입니다.");
        }
        if (stage.order() > 1) {
            ContentHashes.requireValid(previousOutputHash);
        } else if (previousOutputHash != null) {
            throw new IllegalArgumentException("첫 단계에는 previousOutputHash를 전달할 수 없습니다.");
        }
        String canonical = CanonicalJsonHasher.computeCanonicalHash(canonicalInput);
        String material = CONTRACT_VERSION + '\n' + stage.name() + '\n'
                + (previousOutputHash == null ? "ROOT" : previousOutputHash) + '\n' + canonical;
        return ContentHashes.sha256Hex(material.getBytes(StandardCharsets.UTF_8));
    }

    public static String outputHash(byte[] output) {
        if (output == null) {
            throw new IllegalArgumentException("output은 필수입니다.");
        }
        return ContentHashes.sha256Hex(output);
    }

    /** 자동 재시도는 없으며 명시적 재시도 요청의 조건만 판정한다. */
    public static boolean canRetry(
            ThymeleafGenerationStage stage, String previousInputHash, String requestedInputHash,
            boolean inputChanged, boolean environmentRecovered) {
        if (stage == null) {
            return false;
        }
        ContentHashes.requireValid(previousInputHash);
        ContentHashes.requireValid(requestedInputHash);
        return switch (stage.retryPolicy()) {
            case SAME_INPUT_IDEMPOTENT -> previousInputHash.equals(requestedInputHash) && !inputChanged;
            case AFTER_INPUT_CHANGE -> inputChanged && !previousInputHash.equals(requestedInputHash);
            case AFTER_ENVIRONMENT_RECOVERY -> environmentRecovered
                    && previousInputHash.equals(requestedInputHash) && !inputChanged;
        };
    }
}
