package com.krdevops.springai.model.crud;

/**
 * CrudSchemaQueryService.fetchColumns() 반환값을 타입 안전하게 표현하는 record.
 * MySQL information_schema 컬럼 기준 키:
 *   COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, IS_NULLABLE, COLUMN_KEY, COLUMN_COMMENT
 */
public record ColumnMeta(
        String columnName,   // DB 컬럼명 (예: EMPLYR_ID)
        String dataType,     // MySQL DATA_TYPE 소문자 (예: varchar, int, datetime)
        int columnSize,      // CHARACTER_MAXIMUM_LENGTH (문자열 타입 외에는 0)
        boolean nullable,    // IS_NULLABLE != "NO" → true
        boolean pk,          // COLUMN_KEY = "PRI" → true
        String remarks       // COLUMN_COMMENT
) {}
