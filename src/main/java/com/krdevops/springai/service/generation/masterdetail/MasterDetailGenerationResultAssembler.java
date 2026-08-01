package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import org.springframework.stereotype.Component;

import java.util.List;

/** 내부 Master/Detail Pipeline 결과를 기존 결과 VO로 변환한다. */
@Component
public class MasterDetailGenerationResultAssembler {

    public MasterDetailOrchestrationResult assemble(MasterDetailGenerationCommand command,
                                                     MasterDetailPipelineResult result) {
        MasterDetailGenerationPlan plan = result.plan();
        if (plan.failed()) {
            return new MasterDetailOrchestrationResult(
                    plan.failure().kind() == MasterDetailPlanFailure.Kind.TABLE_NOT_FOUND,
                    command.database(), command.masterTable(), command.detailTable(), command.domain(),
                    command.outputPath().toString(), List.of(), plan.failure().messages(),
                    plan.failure().summary(), "");
        }
        List<String> failed = new java.util.ArrayList<>(result.execution() == null ? List.of()
                : result.execution().failedFiles().stream().map(GenerationFailure::description).toList());
        failed.addAll(result.failures().stream().map(GenerationFailure::description).toList());
        return new MasterDetailOrchestrationResult(false, command.database(), command.masterTable(),
                command.detailTable(), command.domain(), command.outputPath().toString(),
                result.execution() == null ? List.of() : result.execution().succeededNames(),
                failed, result.validationSummary(), result.historySummary());
    }
}
