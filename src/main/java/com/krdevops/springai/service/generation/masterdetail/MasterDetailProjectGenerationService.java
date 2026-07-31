package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.MasterDetailOrchestrationService;
import com.krdevops.springai.service.generation.api.GenerateMasterDetailProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider=auto 경로의 얇은 어댑터. {@code MasterDetailOrchestrationService}는 Design Context를
 * 스스로 조회하지 않고 이미 해석된 {@link ScreenSpecification}을 인자로 받으므로, 이 Use Case가
 * {@link GenerationDesignContextService}를 먼저 호출해 해석한다. {@code MasterDetailOrchestrationService}
 * 내부 로직은 이번 WP에서 수정하지 않는다({@code ORT-PRN-010}).
 */
@Service
@RequiredArgsConstructor
public class MasterDetailProjectGenerationService implements GenerateMasterDetailProjectUseCase {

    private final GenerationDesignContextService generationDesignContextService;
    private final MasterDetailOrchestrationService masterDetailOrchestrationService;

    @Override
    public MasterDetailOrchestrationResult execute(MasterDetailGenerationCommand command) {
        ScreenSpecification screenSpecification = generationDesignContextService.resolve(
                command.database(), command.masterTable(), command.domain(), "master-detail",
                command.designContext().designReferenceId(), command.designContext().screenSpecificationId());
        return masterDetailOrchestrationService.orchestrate(
                command.database(), command.masterTable(), command.detailTable(),
                command.domain(), command.packageName(), command.outputPath().toString(),
                command.egovVersion(), command.viewType(),
                command.layout().layoutMode(), command.layout().layoutView(), command.layout().breadcrumbView(),
                screenSpecification);
    }
}
