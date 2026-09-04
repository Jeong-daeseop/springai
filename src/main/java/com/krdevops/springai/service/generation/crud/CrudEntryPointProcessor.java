package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.service.WarEntryPointConfigurer;
import com.krdevops.springai.service.generation.layout.ProjectTypeDetector;
import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationFailure;
import com.krdevops.springai.service.generation.model.GenerationStage;
import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationStageProcessor;
import com.krdevops.springai.service.generation.pipeline.ProcessorResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * WAR 기본 진입점(index.jsp)을 생성된 목록 화면 URL로 갱신 —
 * 기존 {@link WarEntryPointConfigurer#configure} 위임.
 *
 * <p>목록 화면 저장이 성공했을 때만 갱신한다(저장 전이면 조용히 생략).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrudEntryPointProcessor implements GenerationStageProcessor {

    static final String ID = "crudEntryPointProcessor";

    private final WarEntryPointConfigurer warEntryPointConfigurer;
    private final ProjectTypeDetector projectTypeDetector;

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
        return true;
    }

    @Override
    public ProcessorResult process(GenerationProcessingContext context) {
        CrudTemplateModel model = context.context().attribute(CrudGenerationAttributes.MODEL);
        CrudViewType viewType = context.context().attribute(CrudGenerationAttributes.VIEW_TYPE);

        // WAR 전용 처리 — Boot 는 index.jsp/web.xml 이 없어 진입점 갱신 대상이 아니다.
        ProjectTypeDetector.ProjectType projectType =
                projectTypeDetector.detect(java.nio.file.Path.of(context.context().outputPath()));
        if (projectType == ProjectTypeDetector.ProjectType.BOOT) {
            log.info("[pipeline] Boot 프로젝트 — WAR 기본 진입점(index.jsp) 갱신 생략");
            return ProcessorResult.ok();
        }

        String listViewName = "Egov" + model.domain() + "List"
                + (viewType == CrudViewType.THYMELEAF ? ".html" : ".jsp");
        if (!context.execution().succeededNames().contains(listViewName)) {
            log.info("[pipeline] 목록 화면 저장 전이므로 index.jsp 기본 진입점 갱신 생략: {}", listViewName);
            return ProcessorResult.ok();
        }

        WarEntryPointConfigurer.ConfigurationResult result =
                warEntryPointConfigurer.configure(
                        context.context().outputPath(), model.route().resolvedListPath());
        if (result.success()) {
            log.info("[pipeline] WAR 기본 진입점 갱신 완료: {}", result.message());
            return ProcessorResult.ok();
        }
        log.error("[pipeline] WAR 기본 진입점 갱신 실패: {}", result.message());
        return ProcessorResult.failed(List.of(
                new GenerationFailure(ID, "WAR 기본 진입점 — " + result.message())));
    }
}
