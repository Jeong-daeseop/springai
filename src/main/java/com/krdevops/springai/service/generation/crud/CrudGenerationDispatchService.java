package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.service.generation.api.BuildCrudPromptUseCase;
import com.krdevops.springai.service.generation.api.DispatchCrudGenerationUseCase;
import com.krdevops.springai.service.generation.api.GenerateCrudProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider(auto vs claude/기타) 분기가 존재하는 유일한 지점. Tool/Facade는 이 분기 로직을
 * 갖지 않는다({@code ORT-PRN-001}).
 */
@Service
@RequiredArgsConstructor
public class CrudGenerationDispatchService implements DispatchCrudGenerationUseCase {

    private final GenerateCrudProjectUseCase generateCrudProjectUseCase;
    private final BuildCrudPromptUseCase buildCrudPromptUseCase;

    @Override
    public CrudToolResult execute(CrudGenerationCommand command) {
        return command.isAuto()
                ? new CrudToolResult.Orchestrated(generateCrudProjectUseCase.execute(command))
                : new CrudToolResult.Prompted(buildCrudPromptUseCase.execute(command));
    }
}
