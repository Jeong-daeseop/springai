package com.krdevops.springai.service;

import com.krdevops.springai.model.crud.CrudTemplateModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrudModelFactoryTest {

    private CrudModelFactory factory;

    @BeforeEach
    void setUp() {
        factory = new CrudModelFactory();
    }

    /** COMTNEMPLYRINFO 기준 2컬럼 최소 픽스처 */
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
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.domain()).isEqualTo("Employer");
        assertThat(model.domainLc()).isEqualTo("employer");
        assertThat(model.tableName()).isEqualTo("COMTNEMPLYRINFO");
        assertThat(model.packageName()).isEqualTo("egovframework.let.emp");
    }

    @Test
    void fromSchema_urlPrefix_derivedFromPackage() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.urlPrefix()).isEqualTo("/emp/employer");
    }

    @Test
    void fromSchema_fields_countMatchesColumns() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.fields()).hasSize(3);
    }

    @Test
    void fromSchema_nonPkFields_excludesPk() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.nonPkFields()).hasSize(2);
        assertThat(model.nonPkFields()).noneMatch(f -> f.pk());
    }

    // ─── PK 탐지 ──────────────────────────────────────────────────────────────

    @Test
    void fromSchema_pkModel_columnNameAndJavaName() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.pk().columnName()).isEqualTo("EMPLYR_ID");
        assertThat(model.pk().javaName()).isEqualTo("emplyrId");
        assertThat(model.pk().javaType()).isEqualTo("String");
    }

    // ─── jakartaValidation 분기 ───────────────────────────────────────────────

    @Test
    void fromSchema_egovVersion50_jakartaValidationTrue() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        assertThat(model.jakartaValidation()).isTrue();
    }

    @Test
    void fromSchema_egovVersionLatest_jakartaValidationTrue() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "latest", sampleColumns());

        assertThat(model.jakartaValidation()).isTrue();
    }

    @Test
    void fromSchema_egovVersion43_jakartaValidationFalse() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "4.3", sampleColumns());

        assertThat(model.jakartaValidation()).isFalse();
    }

    // ─── 필드 변환 검증 ───────────────────────────────────────────────────────

    @Test
    void fromSchema_varcharField_stringTypeTrue_maxLengthSet() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

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
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

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
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        var userNm = model.fields().stream()
            .filter(f -> "userNm".equals(f.javaName()))
            .findFirst().orElseThrow();

        assertThat(userNm.required()).isTrue();
    }

    @Test
    void fromSchema_nullableColumn_requiredFalse() {
        CrudTemplateModel model = factory.fromSchema(
            "COMTNEMPLYRINFO", "Employer", "egovframework.let.emp", "5.0", sampleColumns());

        var age = model.fields().stream()
            .filter(f -> "age".equals(f.javaName()))
            .findFirst().orElseThrow();

        assertThat(age.required()).isFalse();
    }
}
