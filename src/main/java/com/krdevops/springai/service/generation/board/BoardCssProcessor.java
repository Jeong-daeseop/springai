package com.krdevops.springai.service.generation.board;

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

/** 게시판 공통 스타일을 생성된 프로젝트의 styles.css에 보강한다. */
@Component
@RequiredArgsConstructor
public class BoardCssProcessor implements GenerationStageProcessor {

    static final String ID = "boardCssProcessor";

    private final KrdsStylesConfigurer stylesConfigurer;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public GenerationStage stage() {
        return GenerationStage.POST_WRITE;
    }

    @Override
    public boolean supports(GenerationContext context) {
        return "board".equals(context.feature());
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        KrdsStylesConfigurer.CssPatchResult result =
                stylesConfigurer.ensureBoardCrudStyles(context.context().outputPath());
        if (!result.failed()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed("게시판 CSS 보강 실패", List.of(
                new GenerationFailure(ID, "styles.css — " + result.message())));
    }
}
