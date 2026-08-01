package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.CodeService;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** servlet-context.xml에 egovframework.com component-scan을 멱등적으로 추가한다. */
@Component
@RequiredArgsConstructor
public class MasterDetailServletScanProcessor implements GenerationStageProcessor {

    static final String ID = "masterDetailServletScanProcessor";
    private final CodeService codeService;

    @Override public String id() { return ID; }
    @Override public GenerationStage stage() { return GenerationStage.POST_WRITE; }
    @Override public boolean supports(GenerationContext context) { return "master-detail".equals(context.feature()); }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        Path path = Path.of(context.context().outputPath(),
                "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml");
        if (!Files.exists(path)) return ProcessorResult.ok();
        try {
            String xml = Files.readString(path, StandardCharsets.UTF_8);
            if (xml.contains("egovframework.com")) return ProcessorResult.ok();
            String updated = xml.replaceFirst("base-package=\"([^\"]*egovframework\\.let[^\"]*)\"",
                    "base-package=\"$1,egovframework.com\"");
            if (updated.equals(xml)) {
                return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                        "servlet-context.xml — component-scan base-package 패턴을 찾을 수 없습니다.")));
            }
            String result = codeService.saveGeneratedCode(path.toString(), updated);
            if (result != null && !result.startsWith("파일 저장 실패")) return ProcessorResult.ok();
            return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                    "servlet-context.xml — " + result)));
        } catch (Exception exception) {
            return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                    "servlet-context.xml — component-scan 보강 실패: " + exception.getMessage())));
        }
    }
}
