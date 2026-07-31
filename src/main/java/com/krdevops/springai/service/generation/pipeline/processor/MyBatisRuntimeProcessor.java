package com.krdevops.springai.service.generation.pipeline.processor;

import com.krdevops.springai.service.MyBatisRuntimeConfigurer;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MyBatis mapper 스캔 설정 보강 — 기존 {@link MyBatisRuntimeConfigurer#ensureConfigured} 위임.
 */
@Component
@RequiredArgsConstructor
public class MyBatisRuntimeProcessor implements GenerationStageProcessor {

    private final MyBatisRuntimeConfigurer myBatisRuntimeConfigurer;

    @Override
    public String id() {
        return SharedProcessorIds.MYBATIS_RUNTIME;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.POST_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        return true;
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        MyBatisRuntimeConfigurer.ConfigurationResult result = myBatisRuntimeConfigurer.ensureConfigured(
                context.context().outputPath(), context.context().packageName() + ".service.impl");
        if (result.success()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed(List.of(
                new GenerationFailure(SharedProcessorIds.MYBATIS_RUNTIME, "context-common.xml — " + result.message())));
    }
}
