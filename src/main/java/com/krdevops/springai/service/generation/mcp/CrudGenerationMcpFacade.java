package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.generation.api.DispatchCrudGenerationUseCase;
import com.krdevops.springai.service.generation.crud.CrudGenerationCommand;
import com.krdevops.springai.service.generation.crud.CrudToolResult;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import com.krdevops.springai.model.renderer.RendererProfileReference;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * {@code CrudGenerationTool#buildFullCrudPrompt}가 위임하는 Facade. Tool 파라미터 조합을
 * {@link CrudGenerationCommand}로 변환 → {@link DispatchCrudGenerationUseCase} 호출 →
 * {@link CrudGenerationResultFormatter}로 응답 문자열을 만든다.
 */
@Component
@RequiredArgsConstructor
public class CrudGenerationMcpFacade {

    private final DispatchCrudGenerationUseCase dispatchCrudGenerationUseCase;
    private final CrudGenerationResultFormatter formatter;

    public String buildFullCrudPrompt(
            String database, String tableName,
            String domain, String packageName,
            String outputPath, String llmProvider,
            @Nullable String egovVersion,
            @Nullable String viewType,
            @Nullable String layoutMode,
            @Nullable String layoutView,
            @Nullable String breadcrumbView,
            @Nullable String programFileName,
            @Nullable String programUrl,
            @Nullable String programKoreanName,
            @Nullable String programStorePath,
            @Nullable String designReferenceId,
            @Nullable String screenSpecificationId) {

        CrudGenerationCommand command = new CrudGenerationCommand(
                database, tableName, domain, packageName, Path.of(outputPath),
                llmProvider, egovVersion, viewType,
                new LayoutOptions(layoutMode, layoutView, breadcrumbView),
                new ProgramMetadataOverrides(programFileName, programUrl, programKoreanName, programStorePath),
                new DesignContextReference(designReferenceId, screenSpecificationId));

        CrudToolResult result = dispatchCrudGenerationUseCase.execute(command);
        return formatter.format(result);
    }

    /** 승인 Renderer Profile을 명시적으로 고정하는 내부/API 확장 진입점. */
    public String buildFullCrudPrompt(
            String database, String tableName, String domain, String packageName,
            String outputPath, String llmProvider, @Nullable String egovVersion,
            @Nullable String viewType, @Nullable String layoutMode,
            @Nullable String layoutView, @Nullable String breadcrumbView,
            @Nullable String programFileName, @Nullable String programUrl,
            @Nullable String programKoreanName, @Nullable String programStorePath,
            @Nullable String designReferenceId, @Nullable String screenSpecificationId,
            String rendererProfileId, String rendererProfileVersion, String rendererProfileHash) {
        CrudGenerationCommand command = new CrudGenerationCommand(
                database, tableName, domain, packageName, Path.of(outputPath),
                llmProvider, egovVersion, viewType,
                new LayoutOptions(layoutMode, layoutView, breadcrumbView),
                new ProgramMetadataOverrides(
                        programFileName, programUrl, programKoreanName, programStorePath),
                new DesignContextReference(designReferenceId, screenSpecificationId),
                new RendererProfileReference(
                        rendererProfileId, rendererProfileVersion, rendererProfileHash));
        return formatter.format(dispatchCrudGenerationUseCase.execute(command));
    }
}
