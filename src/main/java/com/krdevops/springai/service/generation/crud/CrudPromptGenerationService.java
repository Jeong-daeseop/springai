package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.service.CrudProgramMetadataService;
import com.krdevops.springai.service.CrudPromptBuilderService;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.generation.api.BuildCrudPromptUseCase;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider=claude(그 외) 경로 — Program Metadata 조회와 Design Context 해석을 수행한 뒤
 * {@link CrudPromptBuilderService}로 Prompt 문자열을 빌드한다. 원래 {@code CrudPromptBuilderTool}에
 * 있던 ①②③ 로직(llmProvider 분기 제외)이 실제로 이동하는 지점이다.
 *
 * <p>claude 경로에서도 auto 경로(auto 경로는 {@code CrudOrchestrationService} 내부에서 동일 로직을
 * 수행)와 동일하게 LETTNPROGRMLIST 메타데이터를 미리 해석해 명시 파라미터가 조용히 무시되지
 * 않도록 한다.
 */
@Service
@RequiredArgsConstructor
public class CrudPromptGenerationService implements BuildCrudPromptUseCase {

    private final CrudProgramMetadataService crudProgramMetadataService;
    private final GenerationDesignContextService generationDesignContextService;
    private final CrudPromptBuilderService crudPromptBuilderService;

    @Override
    public PromptGenerationResult execute(CrudGenerationCommand command) {
        CrudGenerationOptions options = command.toGenerationOptions();
        CrudProgramMetadata metadata = crudProgramMetadataService.resolve(
                command.database(), command.domain(), command.tableName(), options);
        ScreenSpecification screenSpecification = generationDesignContextService.resolve(
                command.database(), command.tableName(), metadata.programKoreanName(), "crud",
                command.designContext().designReferenceId(), command.designContext().screenSpecificationId());
        String prompt = crudPromptBuilderService.buildFullCrudPrompt(
                command.database(), command.tableName(), command.domain(), command.packageName(),
                command.outputPath().toString(), command.egovVersion(), command.viewType(),
                command.layout().layoutMode(), command.layout().layoutView(), command.layout().breadcrumbView(),
                metadata, screenSpecification, command.designSystemProfileId());
        return new PromptGenerationResult(prompt);
    }
}
