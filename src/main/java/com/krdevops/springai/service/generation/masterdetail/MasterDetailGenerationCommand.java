package com.krdevops.springai.service.generation.masterdetail;

import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;

import java.nio.file.Path;

/**
 * 1:N 마스터-디테일 CRUD 소스 생성/Prompt 빌드 Command. 명세서 §8.4.
 *
 * <p>{@code llmProvider}가 "auto"이면 {@link MasterDetailProjectGenerationService}(결정론적
 * Pipeline), 그 외(보통 "claude")이면 {@link MasterDetailPromptGenerationService}(Prompt
 * 문자열 빌드)로 분기한다 — 분기 자체는 {@link MasterDetailGenerationDispatchService}만 수행한다.
 */
public record MasterDetailGenerationCommand(
        String database,
        String masterTable,
        String detailTable,
        String domain,
        String packageName,
        Path outputPath,
        String llmProvider,
        String egovVersion,
        String viewType,
        LayoutOptions layout,
        DesignContextReference designContext
) {
    public MasterDetailGenerationCommand {
        llmProvider = (llmProvider == null || llmProvider.isBlank())
                ? "auto" : llmProvider.trim().toLowerCase();
        egovVersion = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        viewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;
        layout = layout == null ? LayoutOptions.empty() : layout;
        designContext = designContext == null ? DesignContextReference.empty() : designContext;
    }

    public boolean isAuto() {
        return "auto".equals(llmProvider);
    }
}
