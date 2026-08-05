package com.krdevops.springai.service.generation.masterdetail;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * servlet-context.xml에 egovframework.com component-scan을 멱등적으로 추가한다.
 *
 * <p>WP7 3차 pass 잔여 항목: 저장은 {@code CodeService.saveGeneratedCode} 직접 호출 대신 공용
 * {@link ApprovedProjectWritePort}({@link ProjectWritePolicy#BEST_EFFORT_COMPATIBILITY})로
 * 위임한다. {@code codeService.validateOutputRoot}가 던지는 {@link SecurityException}은 이 메서드의
 * {@code catch(IOException)}에 걸리지 않고 그대로 전파된다 — 경로 이탈은 "실패"가 아니라 방어해야 할
 * 별도 위반이라는, 이번 pass에서 전환한 다른 write 지점(예: {@code ClasspathAssetCopier})과 동일한
 * 원칙이다.
 */
@Component
@RequiredArgsConstructor
public class MasterDetailServletScanProcessor implements GenerationStageProcessor {

    private static final String RELATIVE_PATH = "src/main/webapp/WEB-INF/spring/appServlet/servlet-context.xml";

    static final String ID = "masterDetailServletScanProcessor";
    private final CodeService codeService;
    private final ApprovedProjectWritePort writePort;

    @Override public String id() { return ID; }
    @Override public GenerationStage stage() { return GenerationStage.POST_WRITE; }
    @Override public boolean supports(GenerationContext context) { return "master-detail".equals(context.feature()); }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        String outputPath = context.context().outputPath();
        Path path = Path.of(outputPath, RELATIVE_PATH);
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
            codeService.validateOutputRoot(outputPath);
            ProjectChangeSet changeSet = new ProjectChangeSet(
                    outputPath, null,
                    List.of(new ProjectChangeSet.FileChange(RELATIVE_PATH, null, updated, null)),
                    List.of(), ProjectWritePolicy.BEST_EFFORT_COMPATIBILITY);
            ApplyOutcome outcome = writePort.apply(changeSet);
            String failureMessage = outcome.failureMessages().get(RELATIVE_PATH);
            if (failureMessage == null) return ProcessorResult.ok();
            return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                    "servlet-context.xml — " + failureMessage)));
        } catch (IOException exception) {
            return ProcessorResult.failed(List.of(new GenerationFailure(ID,
                    "servlet-context.xml — component-scan 보강 실패: " + exception.getMessage())));
        }
    }
}
