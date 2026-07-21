package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudGenerationOptions;
import com.krdevops.springai.model.crud.CrudProgramMetadata;
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
class CrudProgramMetadataServiceTest {

    @Mock JdbcTemplate jdbcTemplate;
    @Mock CrudSchemaQueryService schemaQueryService;
    private CrudProgramMetadataService service;

    @BeforeEach
    void setUp() {
        service = new CrudProgramMetadataService(
                new ProgramMetadataQueryService(jdbcTemplate, schemaQueryService), new BoardProgramUrlParser());
    }

    @Test
    void domainMatch_classifiesRowsByRoleAndResolvesListPath() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of(
                row("EgovBoardMstrList", "게시판 목록조회", "/cop/bbs/SelectBBSMasterInfs.do", "게시판생성관리"),
                row("EgovBoardMstrRegist", "게시판 생성", "/cop/bbs/addBBSMaster.do", null),
                row("EgovBoardMstrUpdt", "게시판 수정", "/cop/bbs/SelectBBSMasterInf.do", null)));

        CrudProgramMetadata result = service.resolve("let", "BoardMstr", "LETTNBBSMASTER",
                CrudGenerationOptions.empty());

        assertThat(result.status()).isEqualTo(CrudProgramMetadata.Status.RESOLVED);
        assertThat(result.programKoreanName()).isEqualTo("게시판 목록조회");
        assertThat(result.upperMenuName()).isEqualTo("게시판생성관리");
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_LIST))
                .isEqualTo("/cop/bbs/SelectBBSMasterInfs.do");
        // LETTNPROGRMLIST URL은 항상 GET 화면 진입점이므로 POST 처리 role(REGIST/UPDT)이 아니라
        // 화면 role(REGIST_VIEW/UPDT_VIEW)로 분류된다 — POST 전용 매핑에 alias를 붙이면 405가 나기 때문.
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_REGIST_VIEW))
                .isEqualTo("/cop/bbs/addBBSMaster.do");
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_UPDT_VIEW))
                .isEqualTo("/cop/bbs/SelectBBSMasterInf.do");
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_REGIST)).isNull();
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_UPDT)).isNull();
        assertThat(result.menuIntegrationStatus()).isEqualTo("DB URL + GNB/LNB 연동");
    }

    @Test
    void ambiguousListCandidates_blocksGeneration() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of(
                row("EgovEmployerList", "직원 목록", "/emp/a.do", null),
                row("EgovEmployerListView", "직원 목록 화면", "/emp/b.do", null)));

        CrudProgramMetadata result = service.resolve("let", "Employer", "LETTNEMPLYRINFO",
                CrudGenerationOptions.empty());

        assertThat(result.status()).isEqualTo(CrudProgramMetadata.Status.AMBIGUOUS);
        assertThat(result.blocksGeneration()).isTrue();
    }

    @Test
    void ambiguousNonListRole_doesNotBlockGeneration_onlyWarns() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of(
                row("EgovNoticeList", "공지 목록", "/bbs/noticeList.do", null),
                row("EgovNoticeRegistOne", "공지 등록1", "/bbs/add1.do", null),
                row("EgovNoticeRegistTwo", "공지 등록2", "/bbs/add2.do", null)));

        CrudProgramMetadata result = service.resolve("let", "Notice", "LETTNBBS",
                CrudGenerationOptions.empty());

        assertThat(result.blocksGeneration()).isFalse();
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_LIST))
                .isEqualTo("/bbs/noticeList.do");
        assertThat(result.registeredPath(CrudProgramMetadataService.ROLE_REGIST_VIEW)).isNull();
        assertThat(result.message()).contains("모호");
    }

    @Test
    void explicitProgramFileName_resolvesAmbiguousListWithoutDroppingSiblingRoles() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of(
                row("EgovBoardMstrList", "게시판 목록조회", "/cop/bbs/a.do", null),
                row("EgovBoardMstrListAdmin", "게시판 목록조회 관리", "/cop/bbs/b.do", null),
                row("EgovBoardMstrRegist", "게시판 생성", "/cop/bbs/c.do", null),
                row("EgovBoardMstrUpdt", "게시판 수정", "/cop/bbs/d.do", null)));

        // programFileName 없이 도메인만으로 조회하면 list 후보가 2건이라 모호해야 한다.
        CrudProgramMetadata ambiguous = service.resolve("let", "BoardMstr", "LETTNBBSMASTER",
                CrudGenerationOptions.empty());
        assertThat(ambiguous.blocksGeneration()).isTrue();

        // 명시로 list 후보를 하나로 고정해도, 같은 도메인의 형제 프로그램(등록/수정 화면)은
        // 계속 조회되어야 한다 — 명시값이 나머지 role 조회까지 막으면 안 된다.
        CrudProgramMetadata resolved = service.resolve("let", "BoardMstr", "LETTNBBSMASTER",
                new CrudGenerationOptions("EgovBoardMstrList", null, null, null));

        assertThat(resolved.blocksGeneration()).isFalse();
        assertThat(resolved.registeredPath(CrudProgramMetadataService.ROLE_LIST))
                .isEqualTo("/cop/bbs/a.do");
        assertThat(resolved.registeredPath(CrudProgramMetadataService.ROLE_REGIST_VIEW))
                .isEqualTo("/cop/bbs/c.do");
        assertThat(resolved.registeredPath(CrudProgramMetadataService.ROLE_UPDT_VIEW))
                .isEqualTo("/cop/bbs/d.do");
    }

    @Test
    void noMatchingProgram_returnsFallback() throws Exception {
        existingProgramAndMenuTables();
        stubProgramRows(List.of());

        CrudProgramMetadata result = service.resolve("let", "Unmatched", "SOME_TABLE",
                CrudGenerationOptions.empty());

        assertThat(result.status()).isEqualTo(CrudProgramMetadata.Status.FALLBACK);
        assertThat(result.blocksGeneration()).isFalse();
    }

    @Test
    void rejectsInvalidDatabaseIdentifier() {
        assertThatThrownBy(() -> service.resolve("let;drop", "Employer", "LETTNEMPLYRINFO",
                CrudGenerationOptions.empty()))
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
