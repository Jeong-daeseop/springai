package com.krdevops.springai.service.generation.mcp;

import com.krdevops.springai.service.ThymeleafLayoutValidator;
import com.krdevops.springai.service.generation.api.GenerateThymeleafLayoutUseCase;
import com.krdevops.springai.service.generation.layout.GenerateThymeleafLayoutCommand;
import com.krdevops.springai.service.generation.layout.LayoutGenerationResult;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

/**
 * Tool의 6개 파라미터를 {@link GenerateThymeleafLayoutCommand}로 변환(기본값 적용)하고
 * Use Case를 호출한 뒤 {@link ThymeleafLayoutResultFormatter}로 MCP 응답 문자열을 만든다.
 */
@Component
@RequiredArgsConstructor
public class ThymeleafLayoutMcpFacade {

    private static final String DEFAULT_PACKAGE_NAME = "egovframework.let.sample";
    private static final String DEFAULT_MENU_TABLE_NAME = "LETTNMENUINFO";
    private static final String DEFAULT_PROGRAM_TABLE_NAME = "LETTNPROGRMLIST";

    private final ThymeleafLayoutValidator thymeleafLayoutValidator;
    private final GenerateThymeleafLayoutUseCase generateThymeleafLayoutUseCase;
    private final ThymeleafLayoutResultFormatter formatter;

    public String generateThymeleafLayout(
            String outputPath,
            @Nullable String layoutBasePath,
            @Nullable Boolean overwriteLayout,
            @Nullable String packageName,
            @Nullable String menuTableName,
            @Nullable String programTableName) {
        GenerateThymeleafLayoutCommand command = toCommand(
                outputPath, layoutBasePath, overwriteLayout, packageName, menuTableName, programTableName);
        LayoutGenerationResult result = generateThymeleafLayoutUseCase.generate(command);
        return formatter.format(result);
    }

    private GenerateThymeleafLayoutCommand toCommand(
            String outputPath,
            @Nullable String layoutBasePath,
            @Nullable Boolean overwriteLayout,
            @Nullable String packageName,
            @Nullable String menuTableName,
            @Nullable String programTableName) {
        String resolvedBasePath = thymeleafLayoutValidator.normalizeLayoutBasePath(layoutBasePath);
        // 기본값은 overwrite=true — 명시적으로 false를 넘긴 경우에만 기존 파일을 보존한다.
        boolean overwrite = !Boolean.FALSE.equals(overwriteLayout);
        boolean packageNameMissing = packageName == null || packageName.isBlank();
        String resolvedPackageName = packageNameMissing ? DEFAULT_PACKAGE_NAME : packageName;
        String resolvedMenuTableName = normalizeTableName(menuTableName, DEFAULT_MENU_TABLE_NAME);
        String resolvedProgramTableName = normalizeTableName(programTableName, DEFAULT_PROGRAM_TABLE_NAME);

        return new GenerateThymeleafLayoutCommand(
                Path.of(outputPath),
                resolvedBasePath,
                overwrite,
                resolvedPackageName,
                packageNameMissing,
                resolvedMenuTableName,
                resolvedProgramTableName);
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value.trim();
    }

    private static String normalizeTableName(String value, String defaultValue) {
        String tableName = defaultIfBlank(value, defaultValue);
        int schemaSeparator = tableName.lastIndexOf('.');
        if (schemaSeparator >= 0) {
            tableName = tableName.substring(schemaSeparator + 1);
        }
        return tableName;
    }
}
