package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaService {

    private final JdbcTemplate jdbcTemplate;

    public String getTableList(String database) {
        log.info("getTableList 호출 — database={}", database);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT TABLE_NAME, TABLE_COMMENT " +
            "FROM INFORMATION_SCHEMA.TABLES " +
            "WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE' " +
            "ORDER BY TABLE_NAME",
            database);

        if (rows.isEmpty()) return "테이블이 없거나 존재하지 않는 데이터베이스입니다: " + database;

        StringBuilder sb = new StringBuilder("DB [" + database + "] 테이블 목록 (" + rows.size() + "개):\n");
        for (Map<String, Object> row : rows) {
            String name    = (String) row.get("TABLE_NAME");
            String comment = (String) row.get("TABLE_COMMENT");
            sb.append(String.format("  - %s%s\n", name,
                (comment != null && !comment.isBlank()) ? " (" + comment + ")" : ""));
        }
        return sb.toString();
    }

    public String getTableSchema(String database, String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT " +
            "  c.COLUMN_NAME, " +
            "  c.DATA_TYPE, " +
            "  c.CHARACTER_MAXIMUM_LENGTH, " +
            "  c.NUMERIC_PRECISION, " +
            "  c.IS_NULLABLE, " +
            "  c.COLUMN_DEFAULT, " +
            "  c.COLUMN_COMMENT, " +
            "  CASE WHEN kcu.COLUMN_NAME IS NOT NULL THEN 'PRI' ELSE '' END AS COLUMN_KEY " +
            "FROM INFORMATION_SCHEMA.COLUMNS c " +
            "LEFT JOIN INFORMATION_SCHEMA.KEY_COLUMN_USAGE kcu " +
            "  ON kcu.TABLE_SCHEMA = c.TABLE_SCHEMA " +
            "  AND kcu.TABLE_NAME = c.TABLE_NAME " +
            "  AND kcu.COLUMN_NAME = c.COLUMN_NAME " +
            "  AND kcu.CONSTRAINT_NAME = 'PRIMARY' " +
            "WHERE c.TABLE_SCHEMA = ? AND c.TABLE_NAME = ? " +
            "ORDER BY c.ORDINAL_POSITION",
            database, tableName);

        if (rows.isEmpty()) return "테이블을 찾을 수 없습니다: " + database + "." + tableName;

        StringBuilder sb = new StringBuilder();
        sb.append("테이블: ").append(database).append(".").append(tableName).append("\n");
        sb.append("eGovFrame 5.x CRUD 소스 생성 정보:\n\n");
        sb.append(String.format("%-30s %-15s %-6s %-8s %-15s %s\n",
                "컬럼명", "타입", "PK", "NULL가능", "기본값", "설명"));
        sb.append("-".repeat(90)).append("\n");

        for (Map<String, Object> col : rows) {
            String colName  = (String) col.get("COLUMN_NAME");
            String dataType = (String) col.get("DATA_TYPE");
            Object charLen  = col.get("CHARACTER_MAXIMUM_LENGTH");
            Object numPrec  = col.get("NUMERIC_PRECISION");
            String isPk     = "PRI".equals(col.get("COLUMN_KEY")) ? "PK" : "";
            String nullable = "YES".equals(col.get("IS_NULLABLE")) ? "Y" : "N";
            String defVal   = col.get("COLUMN_DEFAULT") != null ? col.get("COLUMN_DEFAULT").toString() : "";
            String comment  = col.get("COLUMN_COMMENT") != null ? (String) col.get("COLUMN_COMMENT") : "";

            String typeDesc = dataType;
            if (charLen != null) typeDesc += "(" + charLen + ")";
            else if (numPrec != null) typeDesc += "(" + numPrec + ")";

            sb.append(String.format("%-30s %-15s %-6s %-8s %-15s %s\n",
                    colName, typeDesc, isPk, nullable, defVal, comment));
        }

        sb.append("\n[Java 타입 매핑 참고]\n");
        sb.append("varchar/char/text → String\n");
        sb.append("int/tinyint/smallint → Integer\n");
        sb.append("bigint → Long\n");
        sb.append("decimal/numeric → BigDecimal\n");
        sb.append("datetime/timestamp → LocalDateTime\n");
        sb.append("date → LocalDate\n");

        return sb.toString();
    }
}
