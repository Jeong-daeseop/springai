package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationStage;

/**
 * 특정 Stage에서 부수효과(CSS 보강, 런타임 설정 patch 등)를 수행하는 작은 단위. 명세서 §10.6.
 *
 * <p>{@link #id()}는 Blueprint의 {@code ProcessorStep.processorId}와 매칭되는 키이고,
 * {@link #supports(GenerationContext)}가 false면 해당 문맥에서 조용히 제외된다
 * (예: JSP 생성에서 Thymeleaf 런타임 Processor).
 */
public interface GenerationStageProcessor {

    String id();

    GenerationStage stage();

    boolean supports(GenerationContext context);

    ProcessorResult process(GenerationProcessingContext context);
}
