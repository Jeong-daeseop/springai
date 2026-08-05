package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.model.masterdetail.MasterDetailTemplateModel;
import com.krdevops.springai.model.write.ProjectChangeSet;
import com.krdevops.springai.model.write.ProjectWritePolicy;
import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import com.krdevops.springai.service.write.ApplyOutcome;
import com.krdevops.springai.service.write.ApprovedProjectWritePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Master 목록 URL을 EgovMainController redirect에 반영한다.
 *
 * <p>WP7 3차 pass 잔여 항목: 저장은 {@code CodeService.saveGeneratedCode} 직접 호출 대신 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY} — 단일
 * 파일이라 배치 이점은 없지만 write 경로를 일원화한다)로 위임한다.
 */
@Component
@RequiredArgsConstructor
public class MasterDetailMainControllerProcessor implements GenerationStageProcessor {

    private static final String RELATIVE_PATH = "src/main/java/egovframework/com/web/EgovMainController.java";

    static final String ID = "masterDetailMainControllerProcessor";
    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;

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
        String outputPath = processingContext.context().outputPath();
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

        codeService.validateOutputRoot(outputPath);
        ProjectChangeSet changeSet = new ProjectChangeSet(
                outputPath, null,
                List.of(new ProjectChangeSet.FileChange(RELATIVE_PATH, null, source, null)),
                List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
        ApplyOutcome outcome = writePort.apply(changeSet);
        String failureMessage = outcome.failureMessages().get(RELATIVE_PATH);
        if (failureMessage == null) {
            return ProcessorResult.ok();
        }
        return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                "EgovMainController.java — " + failureMessage)));
    }
}
