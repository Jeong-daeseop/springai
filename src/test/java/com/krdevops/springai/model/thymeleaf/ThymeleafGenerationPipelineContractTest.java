package com.krdevops.springai.model.thymeleaf;

import com.krdevops.springai.model.artifact.ContentHashes;
import com.krdevops.springai.model.contract.GenerationIssue;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThymeleafGenerationPipelineContractTest {

    private static final String HASH_A = ContentHashes.sha256Hex("a".getBytes(StandardCharsets.UTF_8));
    private static final String HASH_B = ContentHashes.sha256Hex("b".getBytes(StandardCharsets.UTF_8));

    @Test
    void exposesExactlyTenOrderedStagesWithInputOutputContracts() {
        List<ThymeleafGenerationStage> stages = ThymeleafGenerationPipelineContract.stages();

        assertThat(stages).hasSize(10);
        assertThat(stages).extracting(ThymeleafGenerationStage::order)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(stages).allSatisfy(stage -> {
            assertThat(stage.inputContract()).isNotBlank();
            assertThat(stage.outputContract()).isNotBlank();
            assertThat(stage.retryPolicy()).isNotNull();
        });
        assertThat(stages.get(0)).isEqualTo(ThymeleafGenerationStage.SOURCE_ANALYSIS);
        assertThat(stages.get(stages.size() - 1)).isEqualTo(
                ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION);
    }

    @Test
    void classifiesFatalErrorAndWarningWithoutAmbiguity() {
        assertThat(ThymeleafGenerationPipelineContract.terminalStatus(List.of(
                issue("WARN", GenerationIssue.Severity.WARNING))))
                .isEqualTo(ThymeleafGenerationStageStatus.SUCCEEDED);
        assertThat(ThymeleafGenerationPipelineContract.terminalStatus(List.of(
                issue("REVIEW", GenerationIssue.Severity.ERROR))))
                .isEqualTo(ThymeleafGenerationStageStatus.REVIEW_REQUIRED);
        assertThat(ThymeleafGenerationPipelineContract.terminalStatus(List.of(
                issue("FATAL", GenerationIssue.Severity.FATAL),
                issue("REVIEW", GenerationIssue.Severity.ERROR))))
                .isEqualTo(ThymeleafGenerationStageStatus.FAILED);

        assertThat(ThymeleafGenerationPipelineContract.blocksFollowingStages(
                ThymeleafGenerationStageStatus.FAILED)).isTrue();
        assertThat(ThymeleafGenerationPipelineContract.blocksFollowingStages(
                ThymeleafGenerationStageStatus.REVIEW_REQUIRED)).isTrue();
        assertThat(ThymeleafGenerationPipelineContract.blocksFollowingStages(
                ThymeleafGenerationStageStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void rejectsUnknownOrInvalidStageTransitions() {
        assertThat(ThymeleafGenerationStageStatus.PENDING.canTransitionTo(
                ThymeleafGenerationStageStatus.RUNNING)).isTrue();
        assertThat(ThymeleafGenerationStageStatus.RUNNING.canTransitionTo(
                ThymeleafGenerationStageStatus.FAILED)).isTrue();
        assertThat(ThymeleafGenerationStageStatus.FAILED.canTransitionTo(
                ThymeleafGenerationStageStatus.RUNNING)).isTrue();
        assertThatThrownBy(() -> ThymeleafGenerationStageStatus.SUCCEEDED.requireTransitionTo(
                ThymeleafGenerationStageStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ThymeleafGenerationStageStatus.valueOf("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputHashIsCanonicalAndChainsPreviousOutput() {
        Map<String, Object> firstOrder = new LinkedHashMap<>();
        firstOrder.put("screenId", "qna-list");
        firstOrder.put("version", 1);
        Map<String, Object> reverseOrder = new LinkedHashMap<>();
        reverseOrder.put("version", 1);
        reverseOrder.put("screenId", "qna-list");

        String first = ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.SOURCE_ANALYSIS, null, firstOrder);
        String same = ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.SOURCE_ANALYSIS, null, reverseOrder);
        String changed = ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.SOURCE_ANALYSIS, null, Map.of("screenId", "qna-detail"));
        String chained = ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.BINDING_CONTRACT, HASH_A, firstOrder);
        String differentParent = ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.BINDING_CONTRACT, HASH_B, firstOrder);

        assertThat(first).isEqualTo(same).isNotEqualTo(changed);
        assertThat(chained).isNotEqualTo(differentParent);
        assertThat(first).matches("^[a-f0-9]{64}$");
        assertThatThrownBy(() -> ThymeleafGenerationPipelineContract.inputHash(
                ThymeleafGenerationStage.BINDING_CONTRACT, null, firstOrder))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void enforcesExplicitRetryPolicies() {
        assertThat(ThymeleafGenerationPipelineContract.canRetry(
                ThymeleafGenerationStage.HTML_SKELETON, HASH_A, HASH_A, false, false)).isTrue();
        assertThat(ThymeleafGenerationPipelineContract.canRetry(
                ThymeleafGenerationStage.COMPONENT_INVENTORY, HASH_A, HASH_B, true, false)).isTrue();
        assertThat(ThymeleafGenerationPipelineContract.canRetry(
                ThymeleafGenerationStage.COMPONENT_INVENTORY, HASH_A, HASH_A, false, false)).isFalse();
        assertThat(ThymeleafGenerationPipelineContract.canRetry(
                ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION,
                HASH_A, HASH_A, false, true)).isTrue();
        assertThat(ThymeleafGenerationPipelineContract.canRetry(
                ThymeleafGenerationStage.BUILD_RENDER_PARITY_VALIDATION,
                HASH_A, HASH_A, false, false)).isFalse();
    }

    @Test
    void validatesExecutionEvidenceAndSkippedStages() {
        Instant now = Instant.parse("2026-08-17T00:00:00Z");
        ThymeleafGenerationStageExecution succeeded = new ThymeleafGenerationStageExecution(
                ThymeleafGenerationStage.SOURCE_ANALYSIS,
                ThymeleafGenerationStageStatus.SUCCEEDED,
                HASH_A, HASH_B, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                now, now.plusSeconds(1), List.of(), List.of(issue("WARN", GenerationIssue.Severity.WARNING)),
                null, 1);
        ThymeleafGenerationStageExecution skipped = new ThymeleafGenerationStageExecution(
                ThymeleafGenerationStage.COMPONENT_INVENTORY,
                ThymeleafGenerationStageStatus.SKIPPED,
                null, null, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                null, null, List.of(), List.of(), ThymeleafGenerationStage.BINDING_CONTRACT, 0);

        assertThat(succeeded.outputHash()).isEqualTo(HASH_B);
        assertThat(skipped.blockedByStage()).isEqualTo(ThymeleafGenerationStage.BINDING_CONTRACT);
        assertThatThrownBy(() -> new ThymeleafGenerationStageExecution(
                ThymeleafGenerationStage.BINDING_CONTRACT,
                ThymeleafGenerationStageStatus.FAILED,
                HASH_A, null, ThymeleafGenerationPipelineContract.CONTRACT_VERSION,
                now, now, List.of(), List.of(issue("WARN", GenerationIssue.Severity.WARNING)), null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FATAL");
    }

    private GenerationIssue issue(String code, GenerationIssue.Severity severity) {
        return new GenerationIssue(code, severity, "R6-050", null, code, null);
    }
}
