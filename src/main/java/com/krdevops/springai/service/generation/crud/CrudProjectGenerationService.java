package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.CrudOrchestrationService;
import com.krdevops.springai.service.generation.api.GenerateCrudProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider=auto 경로의 얇은 어댑터 — Command를 기존 {@link CrudOrchestrationService#orchestrate}
 * 호출로 그대로 변환한다. {@code CrudOrchestrationService} 내부 로직은 이번 WP에서 수정하지 않는다
 * ({@code ORT-PRN-010}).
 */
@Service
@RequiredArgsConstructor
public class CrudProjectGenerationService implements GenerateCrudProjectUseCase {

    private final CrudOrchestrationService crudOrchestrationService;

    @Override
    public CrudOrchestrationResult execute(CrudGenerationCommand command) {
        return crudOrchestrationService.orchestrate(
                command.database(), command.tableName(), command.domain(), command.packageName(),
                command.outputPath().toString(), command.egovVersion(), command.viewType(),
                command.layout().layoutMode(), command.layout().layoutView(), command.layout().breadcrumbView(),
                command.toGenerationOptions());
    }
}
