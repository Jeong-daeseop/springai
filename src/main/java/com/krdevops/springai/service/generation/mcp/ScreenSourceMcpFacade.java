package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.generation.api.GenerateScreenSourceUseCase;
import com.krdevops.springai.service.generation.model.BoardTableOptions;
import com.krdevops.springai.service.generation.model.FeatureType;
import com.krdevops.springai.service.generation.model.GenerateScreenSourceCommand;
import com.krdevops.springai.service.generation.model.GeneratedSource;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import com.krdevops.springai.service.generation.model.ScreenType;
import com.krdevops.springai.service.generation.source.ScreenSourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * {@code CrudPromptBuilderTool}의 12개 단일 화면 미리보기 {@code @Tool} 메서드가 위임하는 Facade.
 * Tool 파라미터 조합을 {@link GenerateScreenSourceCommand}로 변환 → Use Case 호출 →
 * {@link ScreenSourceResultFormatter}로 응답 문자열을 만든다. 테이블 미존재 등으로 파일을 생성하지
 * 못하는 경우 {@link ScreenSourceNotFoundException}의 메시지를 그대로 반환한다.
 */
@Component
@RequiredArgsConstructor
public class ScreenSourceMcpFacade {

    private final GenerateScreenSourceUseCase generateScreenSourceUseCase;
    private final ScreenSourceResultFormatter formatter;

    public String generateCrudScreenSource(
            ScreenType screenType,
            String database,
            String tableName,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        GenerateScreenSourceCommand command = new GenerateScreenSourceCommand(
                FeatureType.CRUD, screenType, database, tableName, null, domain, packageName,
                Path.of(outputPath), egovVersion, viewType, null, null, null);
        return generate(command);
    }

    public String generateBoardScreenSource(
            ScreenType screenType,
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
            @Nullable String programFileName,
            @Nullable String programUrl,
            @Nullable String programKoreanName,
            @Nullable String programStorePath,
            @Nullable String defaultBbsId) {
        GenerateScreenSourceCommand command = new GenerateScreenSourceCommand(
                FeatureType.BOARD, screenType, database, null, null, domain, packageName,
                Path.of(outputPath), egovVersion, viewType,
                new BoardTableOptions(mainTable, masterTable, useTable, fileTable, fileDetailTable),
                new ProgramMetadataOverrides(programFileName, programUrl, programKoreanName, programStorePath),
                defaultBbsId);
        return generate(command);
    }

    public String generateMasterDetailScreenSource(
            ScreenType screenType,
            String database,
            String masterTable,
            String detailTable,
            String domain,
            String packageName,
            String outputPath,
            @Nullable String egovVersion,
            @Nullable String viewType) {
        GenerateScreenSourceCommand command = new GenerateScreenSourceCommand(
                FeatureType.MASTER_DETAIL, screenType, database, masterTable, detailTable, domain, packageName,
                Path.of(outputPath), egovVersion, viewType, null, null, null);
        return generate(command);
    }

    private String generate(GenerateScreenSourceCommand command) {
        try {
            GeneratedSource result = generateScreenSourceUseCase.generate(command);
            return formatter.format(result);
        } catch (ScreenSourceNotFoundException e) {
            return e.getMessage();
        }
    }
}
