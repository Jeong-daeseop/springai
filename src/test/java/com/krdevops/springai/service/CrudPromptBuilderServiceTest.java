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
                jdbcTemplate, commonCodeService, new EgovPromptBuilder(), crudSchemaQueryService);

        when(crudSchemaQueryService.fetchColumns(any(), any())).thenReturn(fakeColumns());
    }

    @Test
    void buildFullCrudPrompt_reuseDefault_excludesLayoutAndGuidesGenerateThymeleafLayout() {
        String result = service.buildFullCrudPrompt(
                "com", "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp",
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
                "com", "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp",
                "/tmp/egov-web", "5.0", "thymeleaf", "create", null, null);

        assertThat(result)
                .contains("16개 레이어 소스")
                .contains("공통 레이아웃 파일 5종도 함께 생성하세요.")
                .contains("src/main/resources/templates/layout/{default,gnb,lnb,breadcrumb,footer}.html")
                .doesNotContain("generateThymeleafLayout(outputPath=..., layoutBasePath=\"layout\")를 실행해야 합니다.");
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
