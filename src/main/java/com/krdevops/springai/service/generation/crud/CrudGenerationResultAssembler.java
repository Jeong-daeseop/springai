package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudRouteModel;
import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pipeline 내부 결과를 기존 {@link CrudOrchestrationResult}(14필드)로 되돌린다.
 *
 * <p>신규 내부 타입({@code GenerationExecution} 등)은 이 경계를 넘어 외부로 노출되지 않는다
 * ({@code ORT-PRN-010} / 명세서 §4.3).
 */
@Component
public class CrudGenerationResultAssembler {

    /** Preflight/Planning 단계 실패 — 파일을 하나도 쓰지 않은 상태다. */
    public CrudOrchestrationResult assemble(CrudGenerationCommand command, CrudPlanFailure failure) {
        if (failure.kind() == CrudPlanFailure.Kind.TABLE_NOT_FOUND) {
            return CrudOrchestrationResult.notFound(command.database(), command.tableName());
        }
        if (failure.kind() == CrudPlanFailure.Kind.LAYOUT_MISSING) {
            // 기존 코드가 9-arg 생성자를 쓰던 분기 — 메타데이터/경로 필드가 비는 동작까지 보존한다.
            return new CrudOrchestrationResult(false, command.database(), command.tableName(),
                    command.domain(), command.outputPath().toString(),
                    List.of(), failure.failedFiles(), failure.validationSummary(), "");
        }
        return new CrudOrchestrationResult(false, command.database(), command.tableName(),
                command.domain(), command.outputPath().toString(),
                List.of(), failure.failedFiles(), failure.validationSummary(), "",
                failure.menuIntegrationStatus(), failure.resolvedProgramName(),
                failure.resolvedProgramUrl(), failure.canonicalUrl(), failure.warnings());
    }

    /** {@code FailurePolicy.STOP} Processor로 중단된 경우 — 저장된 파일이 없다. */
    public CrudOrchestrationResult assembleStopped(
            CrudGenerationCommand command, CrudGenerationPlan plan,
            String stopSummary, List<GenerationFailure> failures) {

        return new CrudOrchestrationResult(false, command.database(), command.tableName(),
                command.domain(), command.outputPath().toString(),
                List.of(), descriptions(failures), stopSummary, "",
                plan.metadata().menuIntegrationStatus(), plan.metadata().programKoreanName(),
                registeredListPath(plan), plan.model().route().canonicalListPath(), plan.warnings());
    }

    public CrudOrchestrationResult assemble(
            CrudGenerationCommand command, CrudGenerationPlan plan, GenerationExecution execution,
            List<GenerationFailure> failures, String validationSummary, String historySummary) {

        return new CrudOrchestrationResult(false, command.database(), command.tableName(),
                command.domain(), command.outputPath().toString(),
                execution.succeededNames(), descriptions(failures), validationSummary, historySummary,
                plan.metadata().menuIntegrationStatus(), plan.metadata().programKoreanName(),
                registeredListPath(plan), plan.model().route().canonicalListPath(), plan.warnings());
    }

    private static String registeredListPath(CrudGenerationPlan plan) {
        CrudRouteModel route = plan.model().route();
        return route.hasListAlias() ? route.registeredListPath() : null;
    }

    private static List<String> descriptions(List<GenerationFailure> failures) {
        return failures.stream().map(GenerationFailure::description).toList();
    }
}
