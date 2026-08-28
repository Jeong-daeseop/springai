package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 게시판(BBS) 소스 생성/Prompt 빌드 Command. 명세서 §8.3.
 *
 * <p>{@code llmProvider}가 "auto"이면 {@link BoardProjectGenerationService}(결정론적 오케스트레이션),
 * 그 외(보통 "claude")이면 {@link BoardPromptGenerationService}(Prompt 문자열 빌드)로 분기한다 —
 * 분기 자체는 {@link BoardGenerationDispatchService}만 수행한다(CRUD의 {@code CrudGenerationCommand}와
 * 동일 원칙). 기존 {@link com.krdevops.springai.service.BoardTableSetResolver} 기본 테이블 정책은
 * Board Planner가 기존 정책과 동일하게 해석한다.
 */
public record BoardGenerationCommand(
        String database,
        String domain,
        String packageName,
        Path outputPath,
        String mainTable,
        String masterTable,
        String useTable,
        String fileTable,
        String fileDetailTable,
        String egovVersion,
        String viewType,
        LayoutOptions layout,
        ProgramMetadataOverrides program,
        String defaultBbsId,
        DesignContextReference designContext,
        String llmProvider
) {
    public BoardGenerationCommand {
        egovVersion = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        viewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;
        layout = layout == null ? LayoutOptions.empty() : layout;
        program = program == null ? ProgramMetadataOverrides.empty() : program;
        designContext = designContext == null ? DesignContextReference.empty() : designContext;
        llmProvider = (llmProvider == null || llmProvider.isBlank())
                ? "auto" : llmProvider.trim().toLowerCase(Locale.ROOT);
    }

    /** llmProvider 도입 전 호출자 호환. */
    public BoardGenerationCommand(
            String database, String domain, String packageName, Path outputPath,
            String mainTable, String masterTable, String useTable, String fileTable,
            String fileDetailTable, String egovVersion, String viewType,
            LayoutOptions layout, ProgramMetadataOverrides program,
            String defaultBbsId, DesignContextReference designContext) {
        this(database, domain, packageName, outputPath, mainTable, masterTable, useTable,
                fileTable, fileDetailTable, egovVersion, viewType, layout, program,
                defaultBbsId, designContext, "auto");
    }

    public boolean isAuto() {
        return "auto".equals(llmProvider);
    }

    /** {@link BoardGenerationOptions} 계약(레거시 협력자 시그니처)으로의 변환. */
    public BoardGenerationOptions toGenerationOptions() {
        return new BoardGenerationOptions(
                program.programFileName(), program.programUrl(),
                program.programKoreanName(), program.programStorePath(), defaultBbsId,
                designContext.designReferenceId(), designContext.screenSpecificationId());
    }
}
