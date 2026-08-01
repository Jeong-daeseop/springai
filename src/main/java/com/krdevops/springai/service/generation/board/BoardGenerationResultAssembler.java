package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 내부 Board Pipeline 결과를 기존 MCP 반환 VO로 변환한다. */
@Component
public class BoardGenerationResultAssembler {

    public BoardOrchestrationResult assemble(BoardGenerationCommand command, BoardGenerationPlan plan,
                                              BoardPipelineResult result, String validationSummary,
                                              String historySummary) {
        if (plan.failed()) {
            return new BoardOrchestrationResult(
                    plan.failure().kind() == BoardPlanFailure.Kind.TABLE_NOT_FOUND,
                    command.database(), command.mainTable(), command.domain(), command.outputPath().toString(),
                    List.of(), plan.failure().messages(), plan.failure().summary(), historySummary,
                    null, null, null, null, command.defaultBbsId(), null, plan.warnings());
        }
        List<String> failures = new ArrayList<>();
        if (result != null) {
            failures.addAll(result.execution() == null ? List.of() : result.execution().failedFiles().stream()
                    .map(GenerationFailure::description).toList());
            failures.addAll(result.processorFailures().stream().map(GenerationFailure::description).toList());
        }
        return new BoardOrchestrationResult(false, command.database(), command.mainTable(), command.domain(),
                command.outputPath().toString(),
                result == null || result.execution() == null ? List.of() : result.execution().succeededNames(),
                failures, validationSummary, historySummary,
                plan.metadata().menuIntegrationStatus(), plan.metadata().programKoreanName(),
                plan.metadata().registeredUrl(), plan.model().route().registeredListPath(),
                plan.metadata().defaultBbsId(), null, plan.warnings());
    }
}
