package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.MasterDetailService;
import com.krdevops.springai.service.generation.api.BuildMasterDetailPromptUseCase;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider=claude(그 외) 경로 — Design Context를 해석한 뒤 {@link MasterDetailService}로
 * Prompt 문자열을 빌드한다. 원래 {@code CrudPromptBuilderTool}에 있던 Design Context 해석 로직이
 * 실제로 이동하는 지점이다.
 */
@Service
@RequiredArgsConstructor
public class MasterDetailPromptGenerationService implements BuildMasterDetailPromptUseCase {

    private final GenerationDesignContextService generationDesignContextService;
    private final MasterDetailService masterDetailService;

    @Override
    public PromptGenerationResult execute(MasterDetailGenerationCommand command) {
        ScreenSpecification screenSpecification = generationDesignContextService.resolve(
                command.database(), command.masterTable(), command.domain(), "master-detail",
                command.designContext().designReferenceId(), command.designContext().screenSpecificationId());
        String prompt = masterDetailService.buildMasterDetailPrompt(
                command.database(), command.masterTable(), command.detailTable(),
                command.domain(), command.packageName(), command.outputPath().toString(), command.viewType(),
                command.layout().layoutMode(), command.layout().layoutView(), command.layout().breadcrumbView(),
                screenSpecification);
        return new PromptGenerationResult(prompt);
    }
}
