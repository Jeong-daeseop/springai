package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudProgramMetadata;
import com.krdevops.springai.model.crud.CrudTemplateModel;
import com.krdevops.springai.model.crud.CrudViewType;
import com.krdevops.springai.model.crud.ScreenSubsetMode;
import com.krdevops.springai.model.design.DataSourceSpec;
import com.krdevops.springai.model.design.FieldSelectionSource;
import com.krdevops.springai.model.design.ActionPlacement;
import com.krdevops.springai.model.design.FieldSource;
import com.krdevops.springai.model.design.FormColumnLayout;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.SearchPanelPlacement;
import com.krdevops.springai.model.design.ScreenFieldBinding;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiFieldRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CrudModelFactoryTest {

    private CrudModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CrudModelFactory();
    }

    /** LETTNEMPLYRINFO 기준 2컬럼 최소 픽스처 */
    private List<Map<String, Object>> sampleColumns() {
        Map<String, Object> pkCol = new LinkedHashMap<>();
        pkCol.put("COLUMN_NAME",             "EMPLYR_ID");
        pkCol.put("DATA_TYPE",               "varchar");
        pkCol.put("CHARACTER_MAXIMUM_LENGTH", 20L);
        pkCol.put("IS_NULLABLE",             "NO");
        pkCol.put("COLUMN_COMMENT",          "직원ID");
        pkCol.put("COLUMN_KEY",              "PRI");

        Map<String, Object> nameCol = new LinkedHashMap<>();
        nameCol.put("COLUMN_NAME",             "USER_NM");
        nameCol.put("DATA_TYPE",               "varchar");
        nameCol.put("CHARACTER_MAXIMUM_LENGTH", 50L);
        nameCol.put("IS_NULLABLE",             "NO");
        nameCol.put("COLUMN_COMMENT",          "사용자명");
        nameCol.put("COLUMN_KEY",              "");

        Map<String, Object> ageCol = new LinkedHashMap<>();
        ageCol.put("COLUMN_NAME",             "AGE");
        ageCol.put("DATA_TYPE",               "int");
        ageCol.put("CHARACTER_MAXIMUM_LENGTH", null);
        ageCol.put("IS_NULLABLE",             "YES");
        ageCol.put("COLUMN_COMMENT",          "나이");
        ageCol.put("COLUMN_KEY",              "");

        return List.of(pkCol, nameCol, ageCol);
    }

    // ─── fromSchema 기본 동작 ─────────────────────────────────────────────────

    @Test
    void fromSchema_domain_mappedCorrectly() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.domain()).isEqualTo("Employer");
        assertThat(model.domainLc()).isEqualTo("employer");
        assertThat(model.tableName()).isEqualTo("LETTNEMPLYRINFO");
        assertThat(model.packageName()).isEqualTo("egovframework.let.emp");
    }

    @Test
    void fromSchema_urlPrefix_derivedFromPackage() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.urlPrefix()).isEqualTo("/emp/employer");
    }

    @Test
    void fromSchema_fields_countMatchesColumns() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.fields()).hasSize(3);
    }

    @Test
    void fromSchema_nonPkFields_excludesPk() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.nonPkFields()).hasSize(2);
        assertThat(model.nonPkFields()).noneMatch(f -> f.pk());
    }

    // ─── 메타데이터 기반 domainKr ────────────────────────────────────────────

    @Test
    void fromSchema_metadataKoreanName_usedAsDomainKr() {
        CrudProgramMetadata metadata = new CrudProgramMetadata(
                "EgovInfoNotice", "/cop/bbs/", "공지사항", "알림정보", Map.of(), null,
                CrudProgramMetadata.Source.DATABASE, CrudProgramMetadata.Status.RESOLVED, null);

        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(), metadata);

        assertThat(model.domainKr()).isEqualTo("공지사항");
    }

    @Test
    void fromSchema_metadataKoreanNameWithScreenSuffix_suffixStripped() {
        // DB 표시명이 "게시판 목록조회"처럼 이미 화면 종류를 포함하면, 템플릿이 다시 "목록"을
        // 붙였을 때 "게시판 목록조회 목록"으로 중복되지 않도록 접미어를 미리 제거해야 한다.
        CrudProgramMetadata metadata = new CrudProgramMetadata(
                "EgovBoardMstrList", "/cop/bbs/", "게시판 목록조회", "게시판생성관리", Map.of(), null,
                CrudProgramMetadata.Source.DATABASE, CrudProgramMetadata.Status.RESOLVED, null);

        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(), metadata);

        assertThat(model.domainKr()).isEqualTo("게시판");
    }

    @Test
    void fromSchema_registeredListUrlWithQuery_propagatedToRouteMenuContextUrl() {
        // Controller 매핑에는 path만 필요하지만(registeredPathByRole), 여러 게시판이 같은 path에
        // bbsId만 다르게 등록되는 경우를 구분하려면 쿼리스트링까지 포함된 원본 URL이
        // route.resolvedMenuContextUrl()을 통해 그대로 보존돼야 한다.
        CrudProgramMetadata metadata = new CrudProgramMetadata(
                "EgovInfoNotice", "/cop/bbs/", "공지사항", "알림정보",
                Map.of(CrudProgramMetadataService.ROLE_LIST, "/cop/bbs/selectBoardList.do"),
                "/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE",
                CrudProgramMetadata.Source.DATABASE, CrudProgramMetadata.Status.RESOLVED, null);

        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(), metadata);

        assertThat(model.route().resolvedMenuContextUrl())
                .isEqualTo("/cop/bbs/selectBoardList.do?bbsId=BBS_NOTICE");
        // Controller @GetMapping 값 자체는 여전히 path만이어야 한다(쿼리스트링을 매핑에 못 씀).
        assertThat(model.route().registeredListPath()).isEqualTo("/cop/bbs/selectBoardList.do");
    }

    @Test
    void fromSchema_noMetadata_fallsBackToTableNameDerivedKoreanName() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.domainKr()).isEqualTo("EMPLYRINFO");
    }

    // ─── PK 탐지 ──────────────────────────────────────────────────────────────

    @Test
    void fromSchema_pkModel_columnNameAndJavaName() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.pk().columnName()).isEqualTo("EMPLYR_ID");
        assertThat(model.pk().javaName()).isEqualTo("emplyrId");
        assertThat(model.pk().javaType()).isEqualTo("String");
    }

    // ─── jakartaValidation 분기 ───────────────────────────────────────────────

    @Test
    void fromSchema_egovVersion50_jakartaValidationTrue() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.jakartaValidation()).isTrue();
    }

    @Test
    void fromSchema_egovVersionLatest_jakartaValidationTrue() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "latest", sampleColumns());

        assertThat(model.jakartaValidation()).isTrue();
    }

    @Test
    void fromSchema_egovVersion43_jakartaValidationFalse() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "4.3", sampleColumns());

        assertThat(model.jakartaValidation()).isFalse();
    }

    // ─── 필드 변환 검증 ───────────────────────────────────────────────────────

    @Test
    void fromSchema_varcharField_stringTypeTrue_maxLengthSet() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        var userNm = model.fields().stream()
            .filter(f -> "userNm".equals(f.javaName()))
            .findFirst().orElseThrow();

        assertThat(userNm.javaType()).isEqualTo("String");
        assertThat(userNm.stringType()).isTrue();
        assertThat(userNm.maxLength()).isEqualTo(50);
        assertThat(userNm.jdbcType()).isEqualTo("VARCHAR");
    }

    @Test
    void fromSchema_intField_stringTypeFalse_maxLengthNull() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        var age = model.fields().stream()
            .filter(f -> "age".equals(f.javaName()))
            .findFirst().orElseThrow();

        assertThat(age.javaType()).isEqualTo("Integer");
        assertThat(age.stringType()).isFalse();
        assertThat(age.maxLength()).isNull();
        assertThat(age.jdbcType()).isEqualTo("INTEGER");
    }

    @Test
    void fromSchema_notNullColumn_requiredTrue() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        var userNm = model.fields().stream()
            .filter(f -> "userNm".equals(f.javaName()))
            .findFirst().orElseThrow();

        assertThat(userNm.required()).isTrue();
    }

    @Test
    void fromSchema_nullableColumn_requiredFalse() {
        CrudTemplateModel model = factory.fromSchema(
            "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        var age = model.fields().stream()
            .filter(f -> "age".equals(f.javaName()))
            .findFirst().orElseThrow();

        assertThat(age.required()).isFalse();
    }

    @Test
    void listAndDetailSubsetAreAppliedIndependentlyForThymeleaf() {
        ScreenSpecification specification = specification(
                new PageSpec("list", "CRUD_LIST", List.of(binding("age", "AGE")), List.of(),
                        FieldSelectionSource.EXPLICIT),
                new PageSpec("detail", "CRUD_DETAIL", List.of(binding("userNm", "USER_NM")), List.of(),
                        FieldSelectionSource.EXPLICIT));

        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF,
                ScreenSubsetMode.LIST_AND_DETAIL, specification);

        assertThat(model.listFields()).extracting("javaName").containsExactly("emplyrId", "age");
        assertThat(model.detailFields()).extracting("javaName").containsExactly("emplyrId", "userNm");
    }

    @Test
    void listAndDetailFieldLabelsUseSpecCustomLabelOverDbComment() {
        ScreenFieldBinding listAge = new ScreenFieldBinding(
                "age", "만 나이", UiFieldRole.GENERIC, FieldSource.column("t", "AGE"),
                true, false, false, false, "NUMBER", 1.0);
        ScreenFieldBinding detailUserNm = new ScreenFieldBinding(
                "userNm", "담당자", UiFieldRole.GENERIC, FieldSource.column("t", "USER_NM"),
                true, false, false, false, "TEXT", 1.0);
        ScreenSpecification specification = specification(
                new PageSpec("list", "CRUD_LIST", List.of(listAge), List.of(),
                        FieldSelectionSource.EXPLICIT),
                new PageSpec("detail", "CRUD_DETAIL", List.of(detailUserNm), List.of(),
                        FieldSelectionSource.EXPLICIT));

        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF,
                ScreenSubsetMode.LIST_AND_DETAIL, specification);

        assertThat(model.listFields()).filteredOn(f -> "age".equals(f.javaName()))
                .extracting("comment").containsExactly("만 나이");
        assertThat(model.detailFields()).filteredOn(f -> "userNm".equals(f.javaName()))
                .extracting("comment").containsExactly("담당자");
    }

    @Test
    void subsetModeNoneUsesFallbackAndDoesNotReinjectQueryDisplayFields() {
        ScreenFieldBinding department = new ScreenFieldBinding(
                "departmentName", "부서", UiFieldRole.DEPARTMENT,
                FieldSource.joinColumn("j1", "ORGNZT_NM"), true, false, false, false, "TEXT", 1.0);
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO",
                List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO"),
                        new DataSourceSpec("org", "com", "COMTNORGNZTINFO", "j1", false,
                                "LEFT", "t.ORGNZT_ID = j1.ORGNZT_ID")),
                List.of(new PageSpec("list", "CRUD_LIST", List.of(department), List.of(),
                        FieldSelectionSource.DESIGN_REFERENCE)),
                List.of(), LocalDateTime.now());

        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF,
                ScreenSubsetMode.NONE, specification);

        assertThat(model.listFields()).extracting("javaName")
                .contains("emplyrId", "userNm", "age")
                .doesNotContain("departmentName");
        assertThat(model.queryContract().displayFields()).extracting("javaName")
                .contains("departmentName");
    }

    @Test
    void fromSchema_formColumnLayout_copiedFromScreenSpecification() {
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.TWO_COLUMN, LocalDateTime.now());

        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF,
                ScreenSubsetMode.NONE, specification);

        assertThat(model.formColumnLayout()).isEqualTo(FormColumnLayout.TWO_COLUMN);
    }

    @Test
    void fromSchema_noScreenSpecification_formColumnLayoutDefaultsToSingleColumn() {
        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.formColumnLayout()).isEqualTo(FormColumnLayout.SINGLE_COLUMN);
    }

    @Test
    void fromSchema_actionAndSearchPanelPlacement_copiedFromScreenSpecification() {
        ScreenSpecification specification = new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(), List.of(), LayoutDensity.STANDARD, FormColumnLayout.SINGLE_COLUMN,
                ActionPlacement.BOTTOM_RIGHT, SearchPanelPlacement.NONE, LocalDateTime.now());

        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns(),
                CrudProgramMetadata.fallback(null), CrudViewType.THYMELEAF,
                ScreenSubsetMode.NONE, specification);

        assertThat(model.actionPlacement()).isEqualTo(ActionPlacement.BOTTOM_RIGHT);
        assertThat(model.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.NONE);
    }

    @Test
    void fromSchema_noScreenSpecification_actionAndSearchPanelPlacementDefaultToTopRightAndAboveTable() {
        CrudTemplateModel model = factory.fromSchema(
                "LETTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.actionPlacement()).isEqualTo(ActionPlacement.TOP_RIGHT);
        assertThat(model.searchPanelPlacement()).isEqualTo(SearchPanelPlacement.ABOVE_TABLE);
    }

    private ScreenSpecification specification(PageSpec... pages) {
        return new ScreenSpecification(
                "spec", 1, ScreenSpecStatus.APPROVED, "직원", "crud", "CRUD_LIST",
                "com", "LETTNEMPLYRINFO", List.of(DataSourceSpec.primary("com", "LETTNEMPLYRINFO")),
                List.of(pages), List.of(), LocalDateTime.now());
    }

    private ScreenFieldBinding binding(String id, String column) {
        return new ScreenFieldBinding(
                id, id, UiFieldRole.GENERIC, FieldSource.column("t", column),
                true, false, false, false, "TEXT", 1.0);
    }
}
