package com.krdevops.springai.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CrudMappingUtilsTest {

    // ─── toCamelCase ──────────────────────────────────────────────────────────

    @Test
    void toCamelCase_singleWord_returnsLowercase() {
        assertThat(CrudMappingUtils.toCamelCase("EMPLYR")).isEqualTo("emplyr");
    }

    @Test
    void toCamelCase_twoWords_returnsLowerCamel() {
        assertThat(CrudMappingUtils.toCamelCase("EMPLYR_ID")).isEqualTo("emplyrId");
    }

    @Test
    void toCamelCase_threeWords_returnsLowerCamel() {
        assertThat(CrudMappingUtils.toCamelCase("USER_NM_KR")).isEqualTo("userNmKr");
    }

    @Test
    void toCamelCase_alreadyLower_returnsLowerCamel() {
        assertThat(CrudMappingUtils.toCamelCase("user_nm")).isEqualTo("userNm");
    }

    @Test
    void toCamelCase_null_returnsEmpty() {
        assertThat(CrudMappingUtils.toCamelCase(null)).isEmpty();
    }

    // ─── mapJavaType ──────────────────────────────────────────────────────────

    @Test
    void mapJavaType_varchar_returnsString() {
        assertThat(CrudMappingUtils.mapJavaType("varchar", 50)).isEqualTo("String");
    }

    @Test
    void mapJavaType_int_returnsInteger() {
        assertThat(CrudMappingUtils.mapJavaType("int", 0)).isEqualTo("Integer");
    }

    @Test
    void mapJavaType_tinyint_returnsInteger() {
        assertThat(CrudMappingUtils.mapJavaType("tinyint", 0)).isEqualTo("Integer");
    }

    @Test
    void mapJavaType_bigint_returnsLong() {
        assertThat(CrudMappingUtils.mapJavaType("bigint", 0)).isEqualTo("Long");
    }

    @Test
    void mapJavaType_decimal_returnsBigDecimal() {
        assertThat(CrudMappingUtils.mapJavaType("decimal", 0)).isEqualTo("java.math.BigDecimal");
    }

    @Test
    void mapJavaType_datetime_returnsString() {
        assertThat(CrudMappingUtils.mapJavaType("datetime", 0)).isEqualTo("String");
    }

    @Test
    void mapJavaType_date_returnsString() {
        assertThat(CrudMappingUtils.mapJavaType("date", 0)).isEqualTo("String");
    }

    @Test
    void mapJavaType_bit_returnsBoolean() {
        assertThat(CrudMappingUtils.mapJavaType("bit", 0)).isEqualTo("Boolean");
    }

    @Test
    void mapJavaType_null_returnsString() {
        assertThat(CrudMappingUtils.mapJavaType(null, 0)).isEqualTo("String");
    }

    @Test
    void mapJavaType_unknownType_returnsString() {
        assertThat(CrudMappingUtils.mapJavaType("json", 0)).isEqualTo("String");
    }

    // ─── mapJdbcType ──────────────────────────────────────────────────────────

    @Test
    void mapJdbcType_varchar_returnsVARCHAR() {
        assertThat(CrudMappingUtils.mapJdbcType("varchar")).isEqualTo("VARCHAR");
    }

    @Test
    void mapJdbcType_int_returnsINTEGER() {
        assertThat(CrudMappingUtils.mapJdbcType("int")).isEqualTo("INTEGER");
    }

    @Test
    void mapJdbcType_bigint_returnsBIGINT() {
        assertThat(CrudMappingUtils.mapJdbcType("bigint")).isEqualTo("BIGINT");
    }

    @Test
    void mapJdbcType_decimal_returnsDECIMAL() {
        assertThat(CrudMappingUtils.mapJdbcType("decimal")).isEqualTo("DECIMAL");
    }

    @Test
    void mapJdbcType_datetime_returnsTIMESTAMP() {
        assertThat(CrudMappingUtils.mapJdbcType("datetime")).isEqualTo("TIMESTAMP");
    }

    @Test
    void mapJdbcType_date_returnsDATE() {
        assertThat(CrudMappingUtils.mapJdbcType("date")).isEqualTo("DATE");
    }

    @Test
    void mapJdbcType_null_returnsVARCHAR() {
        assertThat(CrudMappingUtils.mapJdbcType(null)).isEqualTo("VARCHAR");
    }

    // ─── extractKoreanName ────────────────────────────────────────────────────

    @Test
    void extractKoreanName_LETTN_stripsPrefix() {
        assertThat(CrudMappingUtils.extractKoreanName("LETTNEMPLYRINFO")).isEqualTo("EMPLYRINFO");
    }

    @Test
    void extractKoreanName_LETTS_stripsPrefix() {
        assertThat(CrudMappingUtils.extractKoreanName("LETTSBBSMASTER")).isEqualTo("BBSMASTER");
    }

    @Test
    void extractKoreanName_LETTC_stripsPrefix() {
        assertThat(CrudMappingUtils.extractKoreanName("LETTCCMMNCODE")).isEqualTo("CMMNCODE");
    }

    @Test
    void extractKoreanName_noPrefixTable_returnsAsIs() {
        assertThat(CrudMappingUtils.extractKoreanName("CUSTOM_TABLE")).isEqualTo("CUSTOM_TABLE");
    }

    @Test
    void extractKoreanName_null_returnsEmpty() {
        assertThat(CrudMappingUtils.extractKoreanName(null)).isEmpty();
    }
}
