package com.krdevops.springai.service.generation.model;

import java.nio.file.Path;

/**
 * 단일 화면 Source 미리보기 생성 Command. 명세서 §8.5.
 *
 * <p>{@code primaryTable}은 CRUD의 tableName, 게시판의 mainTable, Master/Detail의 masterTable을 의미하며,
 * {@code secondaryTable}은 Master/Detail의 detailTable에만 사용되고 그 외 featureType에서는 null이다.
 */
public record GenerateScreenSourceCommand(
        FeatureType featureType,
        ScreenType screenType,
        String database,
        String primaryTable,
        String secondaryTable,
        String domain,
        String packageName,
        Path outputPath,
        String egovVersion,
        String viewType,
        BoardTableOptions boardTables,
        ProgramMetadataOverrides program,
        String defaultBbsId
) {}
