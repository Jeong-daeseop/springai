package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.ThymeleafRuntimeConfigurer;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Thymeleaf 런타임 설정 보강 — 기존 {@link ThymeleafRuntimeConfigurer#ensureThymeleafRuntime} 위임.
 * JSP 생성에서는 {@link #supports}가 false를 반환해 제외된다.
 */
@Component
@RequiredArgsConstructor
public class ThymeleafRuntimeProcessor implements GenerationStageProcessor {

    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;

    @Override
    public String id() {
        return SharedProcessorIds.THYMELEAF_RUNTIME;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.POST_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        return CrudViewType.THYMELEAF.value().equals(context.viewType());
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        List<String> failed = new ArrayList<>();
        thymeleafRuntimeConfigurer.ensureThymeleafRuntime(
                context.context().outputPath(), context.context().egovVersion(), failed);
        return ProcessorFailures.toResult(SharedProcessorIds.THYMELEAF_RUNTIME, failed);
    }
}
