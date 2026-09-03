package com.krdevops.springai.service.generation.crud;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import com.krdevops.springai.model.renderer.RendererProfileReference;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * CRUD 전체 소스 생성/Prompt 빌드 Command. 명세서 §8.2.
 *
 * <p>{@code llmProvider}가 "auto"이면 {@link CrudGenerationApplicationService}(결정론적 오케스트레이션),
 * 그 외(보통 "claude")이면 {@link CrudPromptGenerationService}(Prompt 문자열 빌드)로 분기한다 —
 * 분기 자체는 {@link CrudGenerationDispatchService}만 수행한다.
 */
public record CrudGenerationCommand(
        String database,
        String tableName,
        String domain,
        String packageName,
        Path outputPath,
        String llmProvider,
        String egovVersion,
        String viewType,
        LayoutOptions layout,
        ProgramMetadataOverrides program,
        DesignContextReference designContext,
        RendererProfileReference rendererProfileReference,
        @Nullable String designSystemProfileId
) {
    public CrudGenerationCommand {
        llmProvider = (llmProvider == null || llmProvider.isBlank())
                ? "auto" : llmProvider.trim().toLowerCase();
        egovVersion = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        viewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;
        layout = layout == null ? LayoutOptions.empty() : layout;
        program = program == null ? ProgramMetadataOverrides.empty() : program;
        designContext = designContext == null ? DesignContextReference.empty() : designContext;
        rendererProfileReference = rendererProfileReference == null
                ? RendererProfileReference.defaultThymeleafKrds() : rendererProfileReference;
        designSystemProfileId = (designSystemProfileId == null || designSystemProfileId.isBlank())
                ? null : designSystemProfileId.trim();
    }

    /** designSystemProfileId 도입 전 12-arg 호출자 호환. */
    public CrudGenerationCommand(
            String database,
            String tableName,
            String domain,
            String packageName,
            Path outputPath,
            String llmProvider,
            String egovVersion,
            String viewType,
            LayoutOptions layout,
            ProgramMetadataOverrides program,
            DesignContextReference designContext,
            RendererProfileReference rendererProfileReference) {
        this(database, tableName, domain, packageName, outputPath, llmProvider, egovVersion,
                viewType, layout, program, designContext, rendererProfileReference, null);
    }

    /** RendererProfileReference 도입 전 Tool·Java 호출자 호환. */
    public CrudGenerationCommand(
            String database,
            String tableName,
            String domain,
            String packageName,
            Path outputPath,
            String llmProvider,
            String egovVersion,
            String viewType,
            LayoutOptions layout,
            ProgramMetadataOverrides program,
            DesignContextReference designContext) {
        this(database, tableName, domain, packageName, outputPath, llmProvider, egovVersion,
                viewType, layout, program, designContext,
                RendererProfileReference.defaultThymeleafKrds(), null);
    }

    public boolean isAuto() {
        return "auto".equals(llmProvider);
    }

    /** {@link CrudGenerationOptions} 계약(레거시 협력자 시그니처)으로의 변환. */
    public CrudGenerationOptions toGenerationOptions() {
        return new CrudGenerationOptions(
                program.programFileName(), program.programUrl(),
                program.programKoreanName(), program.programStorePath(),
                designContext.designReferenceId(), designContext.screenSpecificationId());
    }
}
