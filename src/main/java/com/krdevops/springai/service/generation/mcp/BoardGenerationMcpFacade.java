package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import com.krdevops.springai.service.generation.board.BoardGenerationCommand;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * {@code BoardGenerationTool#buildBoardFeature}가 위임하는 Facade. Tool 파라미터 조합을
 * {@link BoardGenerationCommand}로 변환 → {@link GenerateBoardProjectUseCase} 호출(분기 없음) →
 * {@link BoardGenerationResultFormatter}로 응답 문자열을 만든다.
 */
@Component
@RequiredArgsConstructor
public class BoardGenerationMcpFacade {

    private final GenerateBoardProjectUseCase generateBoardProjectUseCase;
    private final BoardGenerationResultFormatter formatter;

    public String buildBoardFeature(
            String database,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String mainTable,
            @Nullable String masterTable,
            @Nullable String useTable,
            @Nullable String fileTable,
            @Nullable String fileDetailTable,
            @Nullable String egovVersion,
            @Nullable String viewType,
            @Nullable String layoutMode,
            @Nullable String layoutView,
            @Nullable String breadcrumbView,
            @Nullable String programFileName,
            @Nullable String programUrl,
            @Nullable String programKoreanName,
            @Nullable String programStorePath,
            @Nullable String defaultBbsId,
            @Nullable String designReferenceId,
            @Nullable String screenSpecificationId) {

        BoardGenerationCommand command = new BoardGenerationCommand(
                database, domain, packageName, Path.of(outputPath),
                mainTable, masterTable, useTable, fileTable, fileDetailTable,
                egovVersion, viewType,
                new LayoutOptions(layoutMode, layoutView, breadcrumbView),
                new ProgramMetadataOverrides(programFileName, programUrl, programKoreanName, programStorePath),
                defaultBbsId,
                new DesignContextReference(designReferenceId, screenSpecificationId));

        BoardOrchestrationResult result = generateBoardProjectUseCase.execute(command);
        return formatter.format(result);
    }
}
