package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.generation.api.BuildMasterDetailPromptUseCase;
import com.krdevops.springai.service.generation.api.DispatchMasterDetailGenerationUseCase;
import com.krdevops.springai.service.generation.api.GenerateMasterDetailProjectUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * llmProvider(auto vs claude/기타) 분기가 존재하는 유일한 지점. Tool/Facade는 이 분기 로직을
 * 갖지 않는다({@code ORT-PRN-001}).
 */
@Service
@RequiredArgsConstructor
public class MasterDetailGenerationDispatchService implements DispatchMasterDetailGenerationUseCase {

    private final GenerateMasterDetailProjectUseCase generateMasterDetailProjectUseCase;
    private final BuildMasterDetailPromptUseCase buildMasterDetailPromptUseCase;

    @Override
    public MasterDetailToolResult execute(MasterDetailGenerationCommand command) {
        return command.isAuto()
                ? new MasterDetailToolResult.Orchestrated(generateMasterDetailProjectUseCase.execute(command))
                : new MasterDetailToolResult.Prompted(buildMasterDetailPromptUseCase.execute(command));
    }
}
