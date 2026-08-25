package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.ApprovalMode;
import com.krdevops.springai.model.controlplane.EvidenceRecordingStatus;
import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReleaseReadinessEvaluatorTest {

    private final ReleaseReadinessEvaluator evaluator = new ReleaseReadinessEvaluator();

    @Test
    void 증적이_없으면_실패로_소급하지_않고_NOT_RECORDED와_누락_Gate를_반환한다() {
        var result = evaluator.evaluate(operation(GenerationOperationStatus.APPLIED), List.of());

        assertThat(result.releaseReady()).isFalse();
        assertThat(result.validationEvidenceStatus()).isEqualTo(EvidenceRecordingStatus.NOT_RECORDED);
        assertThat(result.failedGateNames()).isEmpty();
        assertThat(result.missingGateNames()).containsExactly("BINDING", "BUILD", "RENDER");
    }

    @Test
    void 필수_Gate가_통과하고_적용된_Operation만_배포_준비로_판정한다() {
        List<ValidationEvidence> evidence = List.of(
                evidence(ValidationEvidence.GateType.BINDING, ValidationEvidence.Status.PASSED),
                evidence(ValidationEvidence.GateType.BUILD, ValidationEvidence.Status.PASSED),
                evidence(ValidationEvidence.GateType.RENDER, ValidationEvidence.Status.PASSED));

        assertThat(evaluator.evaluate(operation(GenerationOperationStatus.APPLIED), evidence).releaseReady()).isTrue();
        assertThat(evaluator.evaluate(operation(GenerationOperationStatus.CONFLICT), evidence).releaseReady()).isFalse();
    }

    @Test
    void BLOCK_실패와_필수_Gate_누락을_구분한다() {
        List<ValidationEvidence> evidence = List.of(
                evidence(ValidationEvidence.GateType.BINDING, ValidationEvidence.Status.FAILED),
                evidence(ValidationEvidence.GateType.RENDER, ValidationEvidence.Status.PASSED));

        var result = evaluator.evaluate(operation(GenerationOperationStatus.APPLIED), evidence);

        assertThat(result.failedGateNames()).containsExactly("BINDING");
        assertThat(result.missingGateNames()).containsExactly("BUILD");
    }

    @Test
    void SKIPPED는_필수_Gate_충족으로_보지_않고_WARN_실패는_BLOCK_실패로_분류하지_않는다() {
        ValidationEvidence skippedBuild = new ValidationEvidence("skip", "op",
                ValidationEvidence.GateType.BUILD, ValidationEvidence.Status.SKIPPED,
                ValidationEvidence.Severity.INFO, List.of(), List.of(), null, null, "test", Instant.now());
        ValidationEvidence warnBinding = new ValidationEvidence("warn", "op",
                ValidationEvidence.GateType.BINDING, ValidationEvidence.Status.FAILED,
                ValidationEvidence.Severity.WARN, List.of(), List.of(), null, null, "test", Instant.now());

        var result = evaluator.evaluate(operation(GenerationOperationStatus.APPLIED),
                List.of(skippedBuild, warnBinding,
                        evidence(ValidationEvidence.GateType.RENDER, ValidationEvidence.Status.PASSED)));

        assertThat(result.failedGateNames()).isEmpty();
        assertThat(result.missingGateNames()).containsExactly("BUILD");
    }

    @Test
    void 동일한_상태와_증적은_CRUD와_Thymeleaf에서_동일한_준비_판정을_낸다() {
        List<ValidationEvidence> evidence = List.of(
                evidence(ValidationEvidence.GateType.BINDING, ValidationEvidence.Status.PASSED),
                evidence(ValidationEvidence.GateType.BUILD, ValidationEvidence.Status.FAILED));
        var crud = evaluator.evaluate(operation(GenerationSourceType.CRUD, GenerationOperationStatus.APPLIED), evidence);
        var thymeleaf = evaluator.evaluate(operation(GenerationSourceType.THYMELEAF_MIGRATION, GenerationOperationStatus.APPLIED), evidence);
        assertThat(thymeleaf.releaseReady()).isEqualTo(crud.releaseReady());
        assertThat(thymeleaf.validationEvidenceStatus()).isEqualTo(crud.validationEvidenceStatus());
        assertThat(thymeleaf.failedGateNames()).isEqualTo(crud.failedGateNames());
        assertThat(thymeleaf.missingGateNames()).isEqualTo(crud.missingGateNames());
    }

    private ValidationEvidence evidence(ValidationEvidence.GateType type, ValidationEvidence.Status status) {
        return new ValidationEvidence("e-" + type, "op", type, status,
                status == ValidationEvidence.Status.FAILED
                        ? ValidationEvidence.Severity.BLOCK : ValidationEvidence.Severity.INFO,
                List.of(), List.of(), null, null, "test", Instant.now());
    }

    private GenerationOperation operation(GenerationOperationStatus status) {
        return operation(GenerationSourceType.CRUD, status);
    }

    private GenerationOperation operation(GenerationSourceType sourceType, GenerationOperationStatus status) {
        return new GenerationOperation("op", sourceType, "op", "legacy", "op",
                status.name(), "/project", "screen", "EMP", "1", 1,
                ApprovalMode.AUTOMATED_OWNERSHIP_CHECK, status.name(), ProjectWritePolicy.ATOMIC_APPROVED,
                status, List.of(), List.of(), EvidenceRecordingStatus.NOT_RECORDED,
                EvidenceRecordingStatus.NOT_RECORDED, null, null, null,
                Instant.now(), Instant.now(), null);
    }
}
