package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.crud.CrudToolResult;

/** llmProvider(auto vs claude/기타)에 따라 {@link GenerateCrudProjectUseCase}/{@link BuildCrudPromptUseCase}로 분기한다. */
public interface DispatchCrudGenerationUseCase {

    CrudToolResult execute(CrudGenerationCommand command);
}
