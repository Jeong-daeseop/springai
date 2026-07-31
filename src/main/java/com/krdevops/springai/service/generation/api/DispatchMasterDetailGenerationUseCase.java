package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationCommand;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailToolResult;

/** llmProvider(auto vs claude/기타)에 따라 {@link GenerateMasterDetailProjectUseCase}/{@link BuildMasterDetailPromptUseCase}로 분기한다. */
public interface DispatchMasterDetailGenerationUseCase {

    MasterDetailToolResult execute(MasterDetailGenerationCommand command);
}
