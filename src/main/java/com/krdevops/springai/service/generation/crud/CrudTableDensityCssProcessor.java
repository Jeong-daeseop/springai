package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.design.LayoutDensity;
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
 * 비표준 표 밀도용 styles.css 보강 — 기존 {@link KrdsStylesConfigurer#ensureTableDensityStyles} 위임.
 *
 * <p>실패 시 {@code FailurePolicy.STOP}으로 파일을 하나도 쓰지 않고 중단한다.
 */
@Component
@RequiredArgsConstructor
public class CrudTableDensityCssProcessor implements GenerationStageProcessor {

    static final String ID = "crudTableDensityCssProcessor";

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
        return model.layoutDensity() != LayoutDensity.STANDARD;
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        KrdsStylesConfigurer.CssPatchResult css =
                krdsStylesConfigurer.ensureTableDensityStyles(context.context().outputPath());
        if (!css.failed()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed("CSS 보강 실패",
                List.of(new GenerationFailure(ID, "styles.css — " + css.message())));
    }
}
