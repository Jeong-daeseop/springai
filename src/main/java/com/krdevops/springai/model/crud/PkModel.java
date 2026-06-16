package com.krdevops.springai.model.crud;

/**
 * PK 컬럼 정보. CrudTemplateModel 내부에서 단일 참조용으로 보유.
 */
public record PkModel(
        String columnName,  // DB 컬럼명 (예: EMPLYR_ID)
        String javaName,    // 자바 필드명 (예: emplyrId)
        String javaType     // 자바 타입 (예: String)
) {}
