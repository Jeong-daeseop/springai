package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationCommand;
import com.krdevops.springai.service.generation.model.PromptGenerationResult;

/** llmProvider=claude(그 외) 경로 — Claude가 직접 작성할 수 있도록 스키마+지시 Prompt 문자열을 빌드한다. */
public interface BuildMasterDetailPromptUseCase {

    PromptGenerationResult execute(MasterDetailGenerationCommand command);
}
