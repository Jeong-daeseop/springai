package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.CrudOrchestrationResult;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;

/** llmProvider=auto 경로 — 결정론적 오케스트레이션으로 CRUD 소스를 생성·저장한다. */
public interface GenerateCrudProjectUseCase {

    CrudOrchestrationResult execute(CrudGenerationCommand command);
}
