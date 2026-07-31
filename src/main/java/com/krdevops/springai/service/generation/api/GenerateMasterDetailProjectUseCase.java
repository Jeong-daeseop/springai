package com.krdevops.springai.service.generation.api;

import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationCommand;

/** llmProvider=auto 경로 — 결정론적 오케스트레이션으로 마스터-디테일 CRUD 소스를 생성·저장한다. */
public interface GenerateMasterDetailProjectUseCase {

    MasterDetailOrchestrationResult execute(MasterDetailGenerationCommand command);
}
