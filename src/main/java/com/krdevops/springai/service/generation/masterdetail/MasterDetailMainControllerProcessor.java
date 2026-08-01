package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** Master 목록 URL을 EgovMainController redirect에 반영한다. */
@Component
@RequiredArgsConstructor
public class MasterDetailMainControllerProcessor implements GenerationStageProcessor {

    static final String ID = "masterDetailMainControllerProcessor";
    private final CodeService codeService;

    @Override public String id() { return ID; }
    @Override public GenerationStage stage() { return GenerationStage.POST_WRITE; }
    @Override public boolean supports(GenerationContext context) { return "master-detail".equals(context.feature()); }

    @Override
    public ProcessorResult process(GenerationProcessingContext processingContext) {
        MasterDetailTemplateModel model = processingContext.context().attribute("masterDetail.model");
        String fileName = "Egov" + model.domain() + "List."
                + ("thymeleaf".equals(processingContext.context().viewType()) ? "html" : "jsp");
        if (!processingContext.execution().succeededNames().contains(fileName)) {
            return ProcessorResult.ok();
        }
        String path = processingContext.context().outputPath()
                + "/src/main/java/egovframework/com/web/EgovMainController.java";
        String source = """
                package egovframework.com.web;

                import org.springframework.stereotype.Controller;
                import org.springframework.web.bind.annotation.GetMapping;

                @Controller
                public class EgovMainController {
                    @GetMapping("/egovframework/com/main.do")
                    public String main() { return "redirect:%s"; }
                }
                """.formatted(model.urlPrefix() + "List.do");
        String result = codeService.saveGeneratedCode(path, source);
        if (result != null && !result.startsWith("파일 저장 실패")) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                "EgovMainController.java — " + result)));
    }
}
