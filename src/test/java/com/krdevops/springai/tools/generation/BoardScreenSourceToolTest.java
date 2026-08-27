package com.krdevops.springai.tools.generation;

import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.board.BoardTemplateModel;
import com.krdevops.springai.model.crud.FieldModel;
import com.krdevops.springai.service.BoardModelFactory;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.BoardTemplateRenderer;
import com.krdevops.springai.service.generation.mcp.ScreenSourceMcpFacade;
import com.krdevops.springai.service.generation.mcp.ScreenSourceResultFormatter;
import com.krdevops.springai.service.generation.source.BoardScreenSourceGenerator;
import com.krdevops.springai.service.generation.source.ScreenSourceGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실제 등록된 게시판(BBS) 단일 화면 미리보기 MCP 진입점. {@link ScreenSourceMcpFacade}(실제 구현, 하위
 * {@link BoardScreenSourceGenerator}까지 실제 객체)를 통해 리팩터링 전과 동일한 결과를 반환하는지 검증한다.
 *
 * <p>(레거시 {@code CrudPromptBuilderToolTest}에서 이관됨 — 그 클래스는 MCP에 등록되지 않는 죽은 코드였다.)
 */
@ExtendWith(MockitoExtension.class)
class BoardScreenSourceToolTest {

    @Mock BoardTableSetResolver boardTableSetResolver;
    @Mock BoardSchemaService boardSchemaService;
    @Mock BoardProgramMetadataService boardProgramMetadataService;
    @Mock BoardModelFactory boardModelFactory;
    @Mock BoardTemplateRenderer boardTemplateRenderer;

    BoardScreenSourceTool tool;

    private static final FieldModel BBS_ID_FIELD = new FieldModel(
            "BBS_ID", "bbsId", "String", "게시판ID", true, true, true, 20, "VARCHAR");
    private static final FieldModel NTT_ID_FIELD = new FieldModel(
            "NTT_ID", "nttId", "Long", "게시글번호", true, true, false, null, "BIGINT");

    @BeforeEach
    void setUp() {
        BoardScreenSourceGenerator boardGenerator = new BoardScreenSourceGenerator(
                boardTableSetResolver, boardSchemaService, boardProgramMetadataService,
                boardModelFactory, boardTemplateRenderer);
        ScreenSourceGenerationService generationService =
                new ScreenSourceGenerationService(List.of(boardGenerator));
        ScreenSourceMcpFacade facade =
                new ScreenSourceMcpFacade(generationService, new ScreenSourceResultFormatter());
        tool = new BoardScreenSourceTool(facade);
    }

    private BoardTemplateModel boardModel() {
        return new BoardTemplateModel(
                "egovframework.let.bbs", "Bbs", "bbs", "BBS",
                "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "/bbs/bbs", "2026-07-01", "5.0", true,
                BBS_ID_FIELD, NTT_ID_FIELD,
                false, null, "LETTNFILEDETAIL",
                List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD, NTT_ID_FIELD),
                List.of(BBS_ID_FIELD), List.of(BBS_ID_FIELD),
                false);
    }

    private void stubBoardHappyPath(BoardTemplateModel model, String layerKey, String code) {
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardProgramMetadataService.resolve(eq("com"), eq("Bbs"), eq("LETTNBBSMASTER"), any()))
                .thenReturn(BoardProgramMetadata.fallback("fallback"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenReturn(Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))));
        when(boardModelFactory.fromSchemas("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", "LETTNFILEDETAIL",
                "Bbs", "egovframework.let.bbs", "5.0",
                Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))),
                BoardProgramMetadata.fallback("fallback")))
                .thenReturn(model);
        when(boardTemplateRenderer.renderByLayerKey(layerKey, model)).thenReturn(code);
    }

    @Test
    void generateBoardList_usesDefaultTablesAndReturnsJspPath() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspList", "<jsp>board-list</jsp>");

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null,
                null, null, null, null, null);

        verify(boardSchemaService).fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER",
                "LETTNBBSUSE", "LETTNFILE", "LETTNFILEDETAIL");
        assertThat(result).contains("featureType: BOARD");
        assertThat(result).contains("layerKey: jspList");
        assertThat(result).contains("/tmp/out/src/main/webapp/WEB-INF/jsp/bbs/EgovBbsList.jsp");
        assertThat(result).contains("<jsp>board-list</jsp>");
    }

    @Test
    void generateBoardDetail_returnsDetailScreenType() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspDetail", "<jsp>board-detail</jsp>");

        String result = tool.generateBoardDetail(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("screen: Detail");
        assertThat(result).contains("layerKey: jspDetail");
    }

    @Test
    void generateBoardRegist_returnsRegistScreenType() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspRegist", "<jsp>board-regist</jsp>");

        String result = tool.generateBoardRegist(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("screen: Regist");
        assertThat(result).contains("layerKey: jspRegist");
    }

    @Test
    void generateBoardUpdt_returnsUpdtScreenType() {
        var model = boardModel();
        stubBoardHappyPath(model, "jspUpdt", "<jsp>board-updt</jsp>");

        String result = tool.generateBoardUpdt(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null, null, null, null, null, null);

        assertThat(result).contains("screen: Updt");
        assertThat(result).contains("layerKey: jspUpdt");
    }

    @Test
    void generateBoardList_tableNotFound_propagatesMessageFromSchemaService() {
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenThrow(new IllegalArgumentException("게시판 테이블을 찾을 수 없습니다: com.LETTNBBS"));

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null,
                null, null, null, null, null);

        assertThat(result).isEqualTo("게시판 테이블을 찾을 수 없습니다: com.LETTNBBS");
        verify(boardModelFactory, never()).fromSchemas(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void generateBoardList_blocksGeneration_returnsMetadataMessageWithoutRendering() {
        when(boardTableSetResolver.resolve("com", null, null, null, null, null))
                .thenReturn(new BoardTableSet("LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                        "LETTNFILE", "LETTNFILEDETAIL"));
        when(boardSchemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE",
                "LETTNFILE", "LETTNFILEDETAIL"))
                .thenReturn(Map.of("main", List.of(Map.of("COLUMN_NAME", "BBS_ID"))));
        BoardProgramMetadata ambiguous = new BoardProgramMetadata(null, null, null, null, null, null, null,
                BoardProgramMetadata.Source.DATABASE, BoardProgramMetadata.Status.AMBIGUOUS,
                "동일 도메인의 프로그램이 여러 건 발견되었습니다");
        when(boardProgramMetadataService.resolve(eq("com"), eq("Bbs"), eq("LETTNBBSMASTER"), any()))
                .thenReturn(ambiguous);

        String result = tool.generateBoardList(
                "com", "Bbs", "egovframework.let.bbs", "/tmp/out",
                null, null, null, null, null, null, null,
                null, null, null, null, null);

        assertThat(result).isEqualTo("동일 도메인의 프로그램이 여러 건 발견되었습니다");
        verify(boardModelFactory, never()).fromSchemas(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }
}
