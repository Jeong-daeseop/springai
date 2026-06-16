package com.krdevops.springai.util;

/**
 * CRUD 코드 생성에 필요한 정적 변환 유틸리티.
 * CrudPromptBuilderService 의 private 메서드를 이관하여 CrudModelFactory 에서 재사용한다.
 */
public final class CrudMappingUtils {

    private CrudMappingUtils() {}

    /**
     * DB 컬럼명(스네이크 케이스) → Java 필드명(카멜 케이스).
     * 예: EMPLYR_ID → emplyrId
     */
    public static String toCamelCase(String columnName) {
        if (columnName == null) return "";
        String[] parts = columnName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    /**
     * MySQL DATA_TYPE → Java 타입.
     * columnSize 는 향후 확장(예: char(1)→Character 등) 대비용이며 현재는 미사용.
     */
    public static String mapJavaType(String dataType, int columnSize) {
        if (dataType == null) return "String";
        return switch (dataType.toLowerCase()) {
            case "int", "tinyint", "smallint", "mediumint" -> "Integer";
            case "bigint"                                   -> "Long";
            case "decimal", "numeric", "float", "double"   -> "java.math.BigDecimal";
            case "datetime", "timestamp"                    -> "String";
            case "date"                                     -> "String";
            case "bit", "boolean"                           -> "Boolean";
            default                                         -> "String";
        };
    }

    /**
     * MySQL DATA_TYPE → MyBatis jdbcType 문자열.
     * Mapper XML #{field, jdbcType=...} 에 사용된다.
     */
    public static String mapJdbcType(String dataType) {
        if (dataType == null) return "VARCHAR";
        return switch (dataType.toLowerCase()) {
            case "int", "tinyint", "smallint", "mediumint" -> "INTEGER";
            case "bigint"                                   -> "BIGINT";
            case "decimal", "numeric"                       -> "DECIMAL";
            case "float"                                    -> "FLOAT";
            case "double"                                   -> "DOUBLE";
            case "datetime", "timestamp"                    -> "TIMESTAMP";
            case "date"                                     -> "DATE";
            case "bit", "boolean"                           -> "BIT";
            default                                         -> "VARCHAR";
        };
    }

    /**
     * 테이블명에서 eGovFrame 공통 접두어(COMTN / COMTS / COMTC)를 제거하여 한국어 힌트를 추출한다.
     * 예: COMTNEMPLYRINFO → EMPLYRINFO
     */
    public static String extractKoreanName(String tableName) {
        if (tableName == null) return "";
        return tableName
                .replace("COMTN", "")
                .replace("COMTS", "")
                .replace("COMTC", "");
    }
}
