package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.service.KrdsStylesConfigurer;
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
 * 다단 폼 배치용 styles.css 보강 —
 * 기존 {@link KrdsStylesConfigurer#ensureFormColumnLayoutStyles} 위임.
 */
@Component
@RequiredArgsConstructor
public class CrudFormColumnCssProcessor implements GenerationStageProcessor {

    static final String ID = "crudFormColumnCssProcessor";

    private final KrdsStylesConfigurer krdsStylesConfigurer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.PRE_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        CrudTemplateModel model = context.attribute(CrudGenerationAttributes.MODEL);
        return model.formColumnLayout() != FormColumnLayout.SINGLE_COLUMN;
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        KrdsStylesConfigurer.CssPatchResult css =
                krdsStylesConfigurer.ensureFormColumnLayoutStyles(context.context().outputPath());
        if (!css.failed()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed("CSS 보강 실패",
                List.of(new GenerationFailure(ID, "styles.css — " + css.message())));
    }
}
