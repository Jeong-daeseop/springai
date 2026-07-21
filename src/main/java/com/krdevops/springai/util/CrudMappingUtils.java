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
     * 테이블명에서 eGovFrame 공통 접두어(LETTN / LETTS / LETTC)를 제거하여 한국어 힌트를 추출한다.
     * 예: LETTNEMPLYRINFO → EMPLYRINFO
     */
    public static String extractKoreanName(String tableName) {
        if (tableName == null) return "";
        return tableName
                .replace("LETTN", "")
                .replace("LETTS", "")
                .replace("LETTC", "");
    }

    /**
     * LETTNPROGRMLIST.PROGRM_KOREAN_NM처럼 화면 종류(목록/상세/등록/수정/조회)까지 이름에
     * 포함된 값을 화면 제목으로 그대로 쓰면, 템플릿이 다시 "목록"/"상세" 등을 붙일 때
     * "게시판 목록조회 목록"처럼 중복된다. 끝에 붙은 화면 종류 토큰을 제거해 업무명만 남긴다.
     * 예: "게시판 목록조회" → "게시판", "공지사항" → "공지사항"(변화 없음).
     */
    public static String stripScreenTypeSuffix(String koreanName) {
        if (koreanName == null) return null;
        String trimmed = koreanName.trim();
        for (String suffix : SCREEN_TYPE_SUFFIXES) {
            if (trimmed.length() > suffix.length() && trimmed.endsWith(suffix)) {
                return trimmed.substring(0, trimmed.length() - suffix.length()).trim();
            }
        }
        return trimmed;
    }

    // 길이가 긴 복합 토큰을 먼저 검사해야 "목록조회"가 "조회"로 잘못 부분 제거되지 않는다.
    private static final java.util.List<String> SCREEN_TYPE_SUFFIXES = java.util.List.of(
            "목록조회", "상세조회", "목록", "상세", "등록", "수정", "조회");
}
