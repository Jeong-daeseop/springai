package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;

/** llmProvider=claude(그 외) 경로 — Claude가 직접 작성할 수 있도록 스키마+지시 Prompt 문자열을 빌드한다. */
public interface BuildCrudPromptUseCase {

    PromptGenerationResult execute(CrudGenerationCommand command);
}
