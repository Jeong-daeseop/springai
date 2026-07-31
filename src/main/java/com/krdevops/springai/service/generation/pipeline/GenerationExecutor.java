package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationExecution;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;

/** 렌더링된 계획을 파일 시스템에 저장한다(WRITE 단계). 명세서 §10.5. */
public interface GenerationExecutor {

    GenerationExecution execute(RenderedGenerationPlan plan);
}
