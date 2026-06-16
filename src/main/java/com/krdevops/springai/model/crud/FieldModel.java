package com.krdevops.springai.model.crud;

/**
 * 컬럼 1개의 렌더링 메타데이터.
 * FreeMarker 템플릿에서 fields / nonPkFields 리스트로 순회한다.
 */
public record FieldModel(
        String columnName,   // DB 컬럼명 (예: EMPLYR_ID)
        String javaName,     // 자바 필드명 (예: emplyrId)
        String javaType,     // 자바 타입 (예: String, Integer, LocalDate)
        String comment,      // 한국어 코멘트 (COLUMN_COMMENT)
        boolean pk,          // PK 여부
        boolean required,    // NOT NULL 여부 (nullable = false)
        boolean stringType,  // true → @NotBlank, false → @NotNull
        Integer maxLength,   // VARCHAR 길이 (varchar 타입이 아니면 null)
        String jdbcType      // MyBatis jdbcType (예: VARCHAR, INTEGER)
) {}
