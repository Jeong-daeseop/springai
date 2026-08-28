package com.krdevops.springai.service.generation.board;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import com.krdevops.springai.model.board.BoardTableSet;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.service.BoardProgramMetadataService;
import com.krdevops.springai.service.BoardSchemaService;
import com.krdevops.springai.service.BoardTableSetResolver;
import com.krdevops.springai.service.GenerationDesignContextService;
import com.krdevops.springai.service.ScreenSpecificationPromptFormatter;
import com.krdevops.springai.service.generation.model.DesignContextReference;
import com.krdevops.springai.service.generation.model.LayoutOptions;
import com.krdevops.springai.service.generation.model.ProgramMetadataOverrides;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoardPromptGenerationServiceTest {

    private final BoardTableSetResolver tableSetResolver = mock(BoardTableSetResolver.class);
    private final BoardSchemaService schemaService = mock(BoardSchemaService.class);
    private final BoardProgramMetadataService programMetadataService = mock(BoardProgramMetadataService.class);
    private final GenerationDesignContextService designContextService = mock(GenerationDesignContextService.class);
    private final ScreenSpecificationPromptFormatter formatter = mock(ScreenSpecificationPromptFormatter.class);
    private final BoardPromptGenerationService service = new BoardPromptGenerationService(
            tableSetResolver, schemaService, programMetadataService, designContextService, formatter);

    private final BoardTableSet tables = new BoardTableSet(
            "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", null, null);

    @Test
    void promptIncludesTableCompositionAndSchemaAndBusinessRules() {
        when(tableSetResolver.resolve("com", null, null, null, null, null)).thenReturn(tables);
        when(schemaService.fetchBoardSchemas("com", "LETTNBBS", "LETTNBBSMASTER", "LETTNBBSUSE", null, null))
                .thenReturn(Map.of("main", List.of(column("NTT_ID", "bigint", "게시물ID"))));
        BoardProgramMetadata metadata = resolvedMetadata();
        when(programMetadataService.resolve(any(), any(), any(), any())).thenReturn(metadata);
        when(designContextService.resolve(any(), any(), any(), any(), any(), any())).thenReturn(null);

        var result = service.execute(command());

        assertThat(result.prompt()).contains("LETTNBBS");
        assertThat(result.prompt()).contains("LETTNBBSMASTER");
        assertThat(result.prompt()).contains("BBS_ID + NTT_ID");
        assertThat(result.prompt()).contains("NTT_ID");
        assertThat(result.prompt()).contains("공지사항");
    }

    @Test
    void throwsWhenProgramMetadataBlocksGeneration() {
        when(tableSetResolver.resolve("com", null, null, null, null, null)).thenReturn(tables);
        when(schemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("main", List.of(column("NTT_ID", "bigint", "게시물ID"))));
        BoardProgramMetadata blocked = new BoardProgramMetadata(
                null, null, null, null, null, "BBS_NOTICE", null,
                BoardProgramMetadata.Source.EXPLICIT, BoardProgramMetadata.Status.INVALID_BBS_ID,
                "게시판 마스터에 bbsId가 없습니다: BBS_NOTICE");
        when(programMetadataService.resolve(any(), any(), any(), any())).thenReturn(blocked);

        assertThatThrownBy(() -> service.execute(command()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("BBS_NOTICE");
    }

    @Test
    void promptIncludesComponentGeometryGuardrailWhenScreenSpecificationHasGeometry() {
        when(tableSetResolver.resolve("com", null, null, null, null, null)).thenReturn(tables);
        when(schemaService.fetchBoardSchemas(any(), any(), any(), any(), any(), any()))
                .thenReturn(Map.of("main", List.of(column("NTT_ID", "bigint", "게시물ID"))));
        when(programMetadataService.resolve(any(), any(), any(), any())).thenReturn(resolvedMetadata());
        UiDesignSpec.NodeGeometry geometry = new UiDesignSpec.NodeGeometry(
                "1:1", "FRAME", "목록", 0, 0, 1440, 900,
                null, null, null, null, null, null, List.of());
        ScreenSpecification spec = new ScreenSpecification(
                "spec-1", 1, ScreenSpecStatus.APPROVED, "공지사항", "board", "BOARD_LIST",
                "com", "LETTNBBS", List.of(DataSourceSpec.primary("com", "LETTNBBS")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.TOP_RIGHT, SearchPanelPlacement.ABOVE_TABLE, LocalDateTime.now(),
                null, null, List.of(), List.of(geometry));
        when(designContextService.resolve(any(), any(), any(), any(), any(), any())).thenReturn(spec);
        when(formatter.format(spec)).thenReturn("[화면명세 텍스트]");

        var result = service.execute(command());

        assertThat(result.prompt()).contains("[화면명세 텍스트]");
        assertThat(result.prompt()).contains("krds-*/egov-* 클래스 체계");
    }

    private BoardGenerationCommand command() {
        return new BoardGenerationCommand(
                "com", "Notice", "egovframework.let.notice", Path.of("/tmp/out"),
                null, null, null, null, null, "5.0", "jsp",
                LayoutOptions.empty(), ProgramMetadataOverrides.empty(), null,
                DesignContextReference.empty(), "claude");
    }

    private BoardProgramMetadata resolvedMetadata() {
        return new BoardProgramMetadata(
                "EgovNotice", "/cop/bbs/", "공지사항", "/cop/bbs/list.do?bbsId=BBS_NOTICE",
                "/cop/bbs/", "BBS_NOTICE", null,
                BoardProgramMetadata.Source.DATABASE, BoardProgramMetadata.Status.RESOLVED, null);
    }

    private Map<String, Object> column(String name, String type, String comment) {
        return Map.of("COLUMN_NAME", name, "DATA_TYPE", type, "IS_NULLABLE", "NO",
                "COLUMN_KEY", "PRI", "COLUMN_COMMENT", comment);
    }
}
