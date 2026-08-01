package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.MasterDetailOrchestrationResult;
import com.krdevops.springai.service.generation.api.GenerateMasterDetailProjectUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * llmProvider=auto 경로의 Use Case. 모든 실행은 Master/Detail Pipeline을 통해 수행한다.
 */
@Service
public class MasterDetailProjectGenerationService implements GenerateMasterDetailProjectUseCase {

    private final MasterDetailGenerationPipelineService pipeline;
    private final MasterDetailGenerationResultAssembler assembler;
    @Autowired
    public MasterDetailProjectGenerationService(MasterDetailGenerationPipelineService pipeline,
                                                MasterDetailGenerationResultAssembler assembler) {
        this.pipeline = pipeline;
        this.assembler = assembler;
    }

    @Override
    public MasterDetailOrchestrationResult execute(MasterDetailGenerationCommand command) {
        return assembler.assemble(command, pipeline.execute(command));
    }
}
