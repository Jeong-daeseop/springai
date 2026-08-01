package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.service.WarEntryPointConfigurer;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 게시판 목록 URL을 WAR 기본 진입점(index.jsp)에 반영한다. */
@Component
@RequiredArgsConstructor
public class BoardEntryPointProcessor implements GenerationStageProcessor {

    static final String ID = "boardEntryPointProcessor";

    private final WarEntryPointConfigurer entryPointConfigurer;

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
        BoardTemplateModel model = context.context().attribute(BoardGenerationAttributes.MODEL);
        String listName = "Egov" + model.domain() + "List"
                + ("thymeleaf".equals(context.context().viewType()) ? ".html" : ".jsp");
        if (!context.execution().succeededNames().contains(listName)) {
            return ProcessorResult.ok();
        }

        String targetUrl = model.urlPrefix() + "List.do";
        if (model.route().defaultBbsId() != null) {
            targetUrl += "?bbsId=" + model.route().defaultBbsId();
        }
        WarEntryPointConfigurer.ConfigurationResult result =
                entryPointConfigurer.configure(context.context().outputPath(), targetUrl);
        if (result.success()) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed(List.of(
                new GenerationFailure(ID, "WAR 기본 진입점 — " + result.message())));
    }
}
