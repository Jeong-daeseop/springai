package com.krdevops.springai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CrudPromptBuilderService — layoutMode(reuse/create) 분기에 대한 최소 회귀 테스트.
 * 이전에는 CrudPromptBuilderToolTest가 이 서비스를 통째로 mock 처리해 실제 로직이
 * 어디서도 직접 검증되지 않았다.
 */
class CrudPromptBuilderServiceTest {

    CrudSchemaQueryService crudSchemaQueryService;
    CrudPromptBuilderService service;

    @BeforeEach
    void setUp() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CommonCodeService commonCodeService = mock(CommonCodeService.class);
        crudSchemaQueryService = mock(CrudSchemaQueryService.class);

        service = new CrudPromptBuilderService(
                jdbcTemplate, commonCodeService, new EgovPromptBuilder(), crudSchemaQueryService,
                new ScreenSpecificationPromptFormatter());

        when(crudSchemaQueryService.fetchColumns(any(), any())).thenReturn(fakeColumns());
    }

    @Test
    void buildFullCrudPrompt_reuseDefault_excludesLayoutAndGuidesGenerateThymeleafLayout() {
        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf");

        assertThat(result)
                .contains("11개 레이어 소스")
                .contains("공통 레이아웃 파일은 생성하지 말고 기존 파일을 재사용하세요.")
                .contains("generateThymeleafLayout(outputPath=..., layoutBasePath=\"layout\")를 실행해야 합니다.")
                .contains("LETTNMENUINFO와 LETTNPROGRMLIST를 조인")
                .contains("currentMenuId fallback만 설정하세요")
                .doesNotContain("공통 레이아웃 파일 5종도 함께 생성하세요.");
    }

    @Test
    void buildFullCrudPrompt_createMode_includesLayoutGenerationInstruction() {
        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf", "create", null, null);

        assertThat(result)
                .contains("16개 레이어 소스")
                .contains("공통 레이아웃 파일 5종도 함께 생성하세요.")
                .contains("src/main/resources/templates/layout/{default,gnb,lnb,breadcrumb,footer}.html")
                .doesNotContain("generateThymeleafLayout(outputPath=..., layoutBasePath=\"layout\")를 실행해야 합니다.");
    }

    @Test
    void buildFullCrudPrompt_alwaysInstructsMenuContextUrlAndCurrentPageSuffix() {
        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf");

        assertThat(result)
                .contains("menuContextUrl")
                .contains("currentPageSuffix")
                .contains("EgovGnbMenuInterceptor")
                .doesNotContain("항상 path만(쿼리스트링 없이)") // 이전 path-only 안내 잔존 방지
                // 메타데이터가 없을 때(fallback)도 플레이스홀더가 아니라 실제 계산된 URL을 안내해야 한다.
                .contains("이 화면의 값: \"/emp/employerList.do\"")
                .doesNotContain("{{URL_PREFIX}}List.do");
    }

    @Test
    void buildFullCrudPrompt_withRegisteredListUrlContainingQuery_instructsFullUrlNotPathOnly() {
        // 같은 path에 bbsId만 다른 여러 게시판 메뉴를 구분하려면 auto 모드처럼 쿼리스트링까지
        // 포함된 원본 URL을 menuContextUrl 값으로 쓰라고 안내해야 한다 — path만 잘라서 안내하면
        // Claude가 만든 Controller에서도 같은 병합 버그가 재현된다.
        com.krdevops.springai.model.crud.CrudProgramMetadata metadata =
                new com.krdevops.springai.model.crud.CrudProgramMetadata(
                        "EgovInfoNotice", "/cop/bbs/", "공지사항", "알림정보",
                        Map.of(CrudProgramMetadataService.ROLE_LIST, "/cop/bbs/selectBoardList.do"),
                        "/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE",
                        com.krdevops.springai.model.crud.CrudProgramMetadata.Source.DATABASE,
                        com.krdevops.springai.model.crud.CrudProgramMetadata.Status.RESOLVED, null);

        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf", "reuse", null, null, metadata);

        assertThat(result)
                .contains("menuContextUrl")
                .contains("이 화면의 값: \"/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE\"")
                .doesNotContain("항상 path만(쿼리스트링 없이)");
    }

    @Test
    void buildFullCrudPrompt_withMetadata_includesRegisteredUrlGuidance() {
        com.krdevops.springai.model.crud.CrudProgramMetadata metadata =
                new com.krdevops.springai.model.crud.CrudProgramMetadata(
                        "EgovBoardMstrList", "/cop/bbs/", "게시판 목록조회", "게시판생성관리",
                        Map.of(CrudProgramMetadataService.ROLE_LIST, "/cop/bbs/SelectBBSMasterInfs.do"),
                        "/cop/bbs/SelectBBSMasterInfs.do",
                        com.krdevops.springai.model.crud.CrudProgramMetadata.Source.DATABASE,
                        com.krdevops.springai.model.crud.CrudProgramMetadata.Status.RESOLVED, null);

        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf", "reuse", null, null, metadata);

        assertThat(result)
                .contains("/cop/bbs/SelectBBSMasterInfs.do")
                .contains("{{DOMAIN_KR}}         = 게시판"); // 화면 종류 접미어("목록조회") 제거됨
    }

    @Test
    void buildFullCrudPrompt_ambiguousMetadata_returnsBlockingMessage() {
        com.krdevops.springai.model.crud.CrudProgramMetadata ambiguous =
                new com.krdevops.springai.model.crud.CrudProgramMetadata(
                        null, null, null, null, Map.of(), null,
                        com.krdevops.springai.model.crud.CrudProgramMetadata.Source.DATABASE,
                        com.krdevops.springai.model.crud.CrudProgramMetadata.Status.AMBIGUOUS,
                        "list 화면 프로그램 메타데이터가 2건으로 중복되었습니다.");

        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf", "reuse", null, null, ambiguous);

        assertThat(result).contains("메타데이터 검증 실패").contains("중복");
    }

    @Test
    void detailPlaceholderFiltersSensitiveColumnButSchemaSectionsKeepIt() {
        Map<String, Object> password = new HashMap<>();
        password.put("COLUMN_NAME", "PASSWORD_HASH");
        password.put("DATA_TYPE", "varchar");
        password.put("CHARACTER_MAXIMUM_LENGTH", 255L);
        password.put("IS_NULLABLE", "YES");
        password.put("COLUMN_COMMENT", "비밀번호");
        password.put("COLUMN_KEY", "");
        when(crudSchemaQueryService.fetchColumns(any(), any()))
                .thenReturn(List.of(fakeColumns().get(0), password));

        String result = service.buildFullCrudPrompt(
                "com", "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf");

        String detailSection = result.substring(
                result.indexOf("{{JSP_DETAIL_ROWS}}"), result.indexOf("{{JSP_FORM_INPUTS}}"));
        assertThat(detailSection).doesNotContain("passwordHash");
        assertThat(result).contains("private String passwordHash;")
                .contains("상세 화면에는 다음 필드를 표시하지 마세요: PASSWORD_HASH");
    }

    private static List<Map<String, Object>> fakeColumns() {
        Map<String, Object> col = new HashMap<>();
        col.put("COLUMN_NAME", "EMPLYR_ID");
        col.put("DATA_TYPE", "varchar");
        col.put("CHARACTER_MAXIMUM_LENGTH", 20L);
        col.put("IS_NULLABLE", "NO");
        col.put("COLUMN_COMMENT", "직원ID");
        col.put("COLUMN_KEY", "PRI");
        return List.of(col);
    }
}
