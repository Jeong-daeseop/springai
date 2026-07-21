package com.krdevops.springai.service;

import com.krdevops.springai.model.board.BoardGenerationOptions;
import com.krdevops.springai.model.board.BoardProgramMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardProgramMetadataServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock CrudSchemaQueryService schemaQueryService;
    private BoardProgramMetadataService service;

    @BeforeEach
    void setUp() {
        service = new BoardProgramMetadataService(
                new ProgramMetadataQueryService(jdbcTemplate, schemaQueryService), new BoardProgramUrlParser());
    }

    @Test
    void explicitFileNameResolvesDbUrlMenuAndValidatedBbsId() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of(row("EgovInfoNotice", "공지사항",
                "/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE", "알림정보")));
        when(jdbcTemplate.queryForObject(contains("WHERE BBS_ID = ?"), eq(Integer.class), eq("BBS_NOTICE")))
                .thenReturn(1);

        BoardProgramMetadata result = service.resolve("let", "InfoNotice", "LETTNBBSMASTER",
                new BoardGenerationOptions("EgovInfoNotice", null, null, null, null));

        assertThat(result.status()).isEqualTo(BoardProgramMetadata.Status.RESOLVED);
        assertThat(result.programKoreanName()).isEqualTo("공지사항");
        assertThat(result.registeredPath()).isEqualTo("/cop/bbs/selectBoardList.do");
        assertThat(result.defaultBbsId()).isEqualTo("BBS_NOTICE");
        assertThat(result.upperMenuName()).isEqualTo("알림정보");
    }

    @Test
    void domainFallbackDoesNotChooseAmbiguousProgram() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of(
                row("EgovInfoNotice", "공지사항", "/a.do?bbsId=A", "알림정보"),
                row("EgovInfoNoticeAdmin", "공지사항 관리", "/b.do?bbsId=B", "알림정보")));

        BoardProgramMetadata result = service.resolve("let", "Notice", "LETTNBBSMASTER",
                BoardGenerationOptions.empty());

        assertThat(result.status()).isEqualTo(BoardProgramMetadata.Status.AMBIGUOUS);
        assertThat(result.blocksGeneration()).isTrue();
    }

    @Test
    void rejectsInvalidDatabaseIdentifier() {
        assertThatThrownBy(() -> service.resolve("let;drop", "Notice", "LETTNBBSMASTER",
                BoardGenerationOptions.empty()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("database");
    }

    private void existingProgramAndMenuTables() {
        when(schemaQueryService.tableExists("let", "LETTNPROGRMLIST")).thenReturn(true);
        when(schemaQueryService.tableExists("let", "LETTNMENUINFO")).thenReturn(true);
    }

    @SuppressWarnings("unchecked")
    private void stubProgramRows(List<ResultSet> rows) throws Exception {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<Object> mapper = invocation.getArgument(1);
            java.util.ArrayList<Object> mapped = new java.util.ArrayList<>();
            for (int i = 0; i < rows.size(); i++) mapped.add(mapper.mapRow(rows.get(i), i));
            return mapped;
        });
    }

    private ResultSet row(String fileName, String koreanName, String url, String upperMenu) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("PROGRM_FILE_NM")).thenReturn(fileName);
        when(rs.getString("PROGRM_STRE_PATH")).thenReturn("/cop/bbs/");
        when(rs.getString("PROGRM_KOREAN_NM")).thenReturn(koreanName);
        when(rs.getString("URL")).thenReturn(url);
        when(rs.getString("UPPER_MENU_NM")).thenReturn(upperMenu);
        return rs;
    }
}
