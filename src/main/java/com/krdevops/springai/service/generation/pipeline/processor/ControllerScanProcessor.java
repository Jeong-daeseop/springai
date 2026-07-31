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
 * 생성된 Controller 패키지의 component-scan 등록 —
 * 기존 {@link ThymeleafRuntimeConfigurer#ensureControllerComponentScan} 위임.
 */
@Component
@RequiredArgsConstructor
public class ControllerScanProcessor implements GenerationStageProcessor {

    private final ThymeleafRuntimeConfigurer thymeleafRuntimeConfigurer;

    @Override
    public String id() {
        return SharedProcessorIds.CONTROLLER_SCAN;
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
        thymeleafRuntimeConfigurer.ensureControllerComponentScan(
                context.context().outputPath(), context.context().packageName() + ".web", failed);
        return ProcessorFailures.toResult(SharedProcessorIds.CONTROLLER_SCAN, failed);
    }
}
