package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;

import java.nio.file.Path;

/**
 * 게시판(BBS) 소스 생성 Command. 명세서 §8.3.
 *
 * <p>{@code llmProvider} 분기가 없다 — 항상 {@link BoardProjectGenerationService}(결정론적
 * 오케스트레이션)를 통해 생성된다. 기존 {@link com.krdevops.springai.service.BoardTableSetResolver}
 * 기본 테이블 정책은 {@code BoardOrchestrationService} 내부에서 그대로 유지된다.
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
        DesignContextReference designContext
) {
    public BoardGenerationCommand {
        egovVersion = (egovVersion == null || egovVersion.isBlank()) ? "5.0" : egovVersion;
        viewType = (viewType == null || viewType.isBlank()) ? "jsp" : viewType;
        layout = layout == null ? LayoutOptions.empty() : layout;
        program = program == null ? ProgramMetadataOverrides.empty() : program;
        designContext = designContext == null ? DesignContextReference.empty() : designContext;
    }

    /** {@link BoardGenerationOptions} 계약(레거시 협력자 시그니처)으로의 변환. */
    public BoardGenerationOptions toGenerationOptions() {
        return new BoardGenerationOptions(
                program.programFileName(), program.programUrl(),
                program.programKoreanName(), program.programStorePath(), defaultBbsId,
                designContext.designReferenceId(), designContext.screenSpecificationId());
    }
}
