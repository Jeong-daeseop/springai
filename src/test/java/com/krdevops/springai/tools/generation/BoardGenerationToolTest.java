package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.service.BoardOrchestrationResult;
import com.krdevops.springai.service.generation.api.BuildBoardPromptUseCase;
import com.krdevops.springai.service.generation.api.GenerateBoardProjectUseCase;
import com.krdevops.springai.service.generation.board.BoardGenerationDispatchService;
import com.krdevops.springai.service.generation.board.BoardGenerationPipelineService;
import com.krdevops.springai.service.generation.board.BoardGenerationResultAssembler;
import com.krdevops.springai.service.generation.board.BoardPipelineResult;
import com.krdevops.springai.service.generation.board.BoardProjectGenerationService;
import com.krdevops.springai.service.generation.mcp.BoardGenerationMcpFacade;
import com.krdevops.springai.service.generation.mcp.BoardGenerationResultFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 등록된 게시판(BBS) MCP 진입점. {@link BoardGenerationMcpFacade} → {@link GenerateBoardProjectUseCase}까지
 * 실제 객체로 연결하고 최하위 협력자만 Mock 처리해 배선을 검증한다. 이 Tool은 {@code llmProvider} 파라미터가
 * 없고 항상 결정론적 파이프라인만 실행한다.
 *
 * <p>(레거시 {@code CrudPromptBuilderToolTest}에서 이관됨 — 그 클래스는 MCP에 등록되지 않는 죽은 코드였다.)
 */
@ExtendWith(MockitoExtension.class)
class BoardGenerationToolTest {

    @Mock BoardGenerationPipelineService boardGenerationPipelineService;
    @Mock BoardGenerationResultAssembler boardGenerationResultAssembler;

    BoardGenerationTool tool;

    @BeforeEach
    void setUp() {
        GenerateBoardProjectUseCase generateBoardProjectUseCase =
                new BoardProjectGenerationService(boardGenerationPipelineService, boardGenerationResultAssembler);
        BoardGenerationDispatchService dispatchService = new BoardGenerationDispatchService(
                generateBoardProjectUseCase, mock(BuildBoardPromptUseCase.class));
        BoardGenerationMcpFacade facade =
                new BoardGenerationMcpFacade(dispatchService, new BoardGenerationResultFormatter());
        tool = new BoardGenerationTool(facade);
    }

    @Test
    void buildBoardFeaturePassesExplicitMetadataOptions() {
        var pipelineResult = mock(BoardPipelineResult.class);
        when(boardGenerationPipelineService.execute(any())).thenReturn(pipelineResult);
        when(boardGenerationResultAssembler.assemble(any(), any(), eq(pipelineResult), any(), any()))
                .thenReturn(new BoardOrchestrationResult(false, "let", "LETTNBBS", "InfoNotice", "/tmp/out",
                        List.of(), List.of(), "OK", "OK"));

        tool.buildBoardFeature("let", "InfoNotice", "egovframework.let.cop.bbs", "/tmp/out", null,
                null, null, null, null, null, "5.0", "thymeleaf", "reuse", "layout/bbs", "layout/bbs-breadcrumb",
                "EgovInfoNotice", "/cop/bbs/list.do?bbsId=BBS_NOTICE", "공지사항",
                "/cop/bbs/", "BBS_NOTICE", null, null);

        verify(boardGenerationPipelineService).execute(any());
    }

    @Test
    void buildBoardFeature_passesDesignReferenceIdsThroughGenerationOptions() {
        BoardGenerationOptions expectedOptions = new BoardGenerationOptions(
                null, null, null, null, null, "analysis-1", "spec-1");
        var pipelineResult = mock(BoardPipelineResult.class);
        when(boardGenerationPipelineService.execute(any())).thenReturn(pipelineResult);
        when(boardGenerationResultAssembler.assemble(any(), any(), eq(pipelineResult), any(), any()))
                .thenReturn(new BoardOrchestrationResult(false, "let", "LETTNBBS", "InfoNotice", "/tmp/out",
                        List.of(), List.of(), "OK", "OK"));

        tool.buildBoardFeature("let", "InfoNotice", "egovframework.let.cop.bbs", "/tmp/out", null,
                null, null, null, null, null, "5.0", "thymeleaf", "reuse", null, null,
                null, null, null, null, null, "analysis-1", "spec-1");

        verify(boardGenerationPipelineService).execute(any());
    }
}
