package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.generation.api.DispatchMasterDetailGenerationUseCase;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailGenerationCommand;
import com.krdevops.springai.service.generation.masterdetail.MasterDetailToolResult;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * {@code MasterDetailGenerationTool#buildMasterDetailPrompt}가 위임하는 Facade. Tool 파라미터 조합을
 * {@link MasterDetailGenerationCommand}로 변환 → {@link DispatchMasterDetailGenerationUseCase} 호출 →
 * {@link MasterDetailGenerationResultFormatter}로 응답 문자열을 만든다.
 */
@Component
@RequiredArgsConstructor
public class MasterDetailGenerationMcpFacade {

    private final DispatchMasterDetailGenerationUseCase dispatchMasterDetailGenerationUseCase;
    private final MasterDetailGenerationResultFormatter formatter;

    public String buildMasterDetailPrompt(
            String database, String masterTable, String detailTable,
            String domain, String packageName, String outputPath,
            @Nullable String viewType,
            @Nullable String egovVersion,
            @Nullable String llmProvider,
            @Nullable String layoutMode,
            @Nullable String layoutView,
            @Nullable String breadcrumbView,
            @Nullable String designReferenceId,
            @Nullable String screenSpecificationId) {

        MasterDetailGenerationCommand command = new MasterDetailGenerationCommand(
                database, masterTable, detailTable, domain, packageName, Path.of(outputPath),
                llmProvider, egovVersion, viewType,
                new LayoutOptions(layoutMode, layoutView, breadcrumbView),
                new DesignContextReference(designReferenceId, screenSpecificationId));

        MasterDetailToolResult result = dispatchMasterDetailGenerationUseCase.execute(command);
        return formatter.format(result);
    }
}
