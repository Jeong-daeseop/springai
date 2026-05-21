package com.krdevops.springai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * SELECT 전용 SQL 실행 서비스.
 *
 * [보안 원칙]
 * - SELECT / SHOW / EXPLAIN / DESC 만 허용
 * - INSERT / UPDATE / DELETE / DROP / CREATE / ALTER / TRUNCATE 차단
 * - 결과 행 수 최대 100행 제한
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlService {

    private final JdbcTemplate jdbcTemplate;

    private static final int MAX_ROWS = 100;

    private static final Set<String> ALLOWED_KEYWORDS = Set.of(
        "SELECT", "SHOW", "EXPLAIN", "DESC", "DESCRIBE"
    );

    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
        "(?i)\\b(INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|TRUNCATE|REPLACE|MERGE" +
        "|GRANT|REVOKE|CALL|EXEC|EXECUTE|LOAD|OUTFILE|DUMPFILE)\\b"
    );

    // -------------------------------------------------------------------------
    // SELECT 쿼리 실행
    // -------------------------------------------------------------------------

    /**
     * SELECT 쿼리를 실행하고 결과를 테이블 형식 문자열로 반환한다.
     *
     * @param sql 실행할 SELECT SQL
     * @return 결과 테이블 (최대 100행)
     */
    public String executeQuery(String sql) {
        String trimmed = sql.strip();

        String validation = validate(trimmed);
        if (validation != null) return validation;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(trimmed);

            if (rows.isEmpty()) return "결과 없음 (0건)";

            boolean truncated = rows.size() > MAX_ROWS;
            List<Map<String, Object>> display = truncated ? rows.subList(0, MAX_ROWS) : rows;

            log.info("SQL 실행: rows={}, sql={}", rows.size(), trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed);
            return formatTable(display, rows.size(), truncated);

        } catch (Exception e) {
            log.warn("SQL 실행 실패: {}", e.getMessage());
            return "SQL 실행 실패: " + e.getMessage();
        }
    }

    /**
     * 테이블의 샘플 데이터를 조회한다.
     *
     * @param database  데이터베이스명
     * @param tableName 테이블명
     * @param limit     조회 행 수 (최대 100)
     */
    public String getSampleData(String database, String tableName, int limit) {
        if (!isValidIdentifier(database) || !isValidIdentifier(tableName)) {
            return "유효하지 않은 데이터베이스명 또는 테이블명입니다.";
        }
        int safeLimit = Math.min(Math.max(1, limit), MAX_ROWS);
        String sql = "SELECT * FROM `" + database + "`.`" + tableName + "` LIMIT " + safeLimit;
        return executeQuery(sql);
    }

    /**
     * EXPLAIN으로 쿼리 실행 계획을 반환한다.
     *
     * @param sql 분석할 SELECT SQL
     */
    public String explainQuery(String sql) {
        String trimmed = sql.strip();

        String validation = validate(trimmed);
        if (validation != null) return validation;

        String explainSql = trimmed.toUpperCase().startsWith("EXPLAIN") ? trimmed : "EXPLAIN " + trimmed;

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(explainSql);
            if (rows.isEmpty()) return "EXPLAIN 결과 없음";

            log.info("EXPLAIN 실행: sql={}", trimmed.length() > 100 ? trimmed.substring(0, 100) + "..." : trimmed);
            return formatTable(rows, rows.size(), false);

        } catch (Exception e) {
            log.warn("EXPLAIN 실패: {}", e.getMessage());
            return "EXPLAIN 실패: " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // 내부 유틸
    // -------------------------------------------------------------------------

    private String validate(String sql) {
        if (sql.isBlank()) return "SQL이 비어있습니다.";

        // 위험 키워드 차단
        if (DANGEROUS_PATTERN.matcher(sql).find()) {
            return "허용되지 않는 SQL입니다. SELECT / SHOW / EXPLAIN / DESC 만 실행 가능합니다.";
        }

        // 허용 키워드로 시작하는지 확인
        String firstWord = sql.split("\\s+")[0].toUpperCase();
        if (!ALLOWED_KEYWORDS.contains(firstWord)) {
            return "허용되지 않는 SQL입니다. SELECT / SHOW / EXPLAIN / DESC 만 실행 가능합니다.";
        }

        return null; // 통과
    }

    /** 테이블명·DB명에 허용되지 않는 문자가 있으면 false */
    private boolean isValidIdentifier(String name) {
        return name != null && name.matches("[a-zA-Z0-9_가-힣]+");
    }

    /** 결과 행 목록을 고정폭 텍스트 테이블로 포맷 */
    private String formatTable(List<Map<String, Object>> rows, int totalCount, boolean truncated) {
        if (rows.isEmpty()) return "결과 없음";

        List<String> columns = List.copyOf(rows.get(0).keySet());

        // 컬럼별 최대 너비 계산 (헤더 포함)
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).length();
        }
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                Object val = row.get(columns.get(i));
                int len = val == null ? 4 : String.valueOf(val).length();
                widths[i] = Math.max(widths[i], Math.min(len, 40)); // 최대 40자
            }
        }

        StringBuilder sb = new StringBuilder();

        // 구분선
        String separator = buildSeparator(widths);
        sb.append(separator).append("\n");

        // 헤더
        sb.append("|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(" ").append(pad(columns.get(i), widths[i])).append(" |");
        }
        sb.append("\n").append(separator).append("\n");

        // 데이터 행
        for (Map<String, Object> row : rows) {
            sb.append("|");
            for (int i = 0; i < columns.size(); i++) {
                Object val = row.get(columns.get(i));
                String str = val == null ? "NULL" : String.valueOf(val);
                if (str.length() > 40) str = str.substring(0, 37) + "...";
                sb.append(" ").append(pad(str, widths[i])).append(" |");
            }
            sb.append("\n");
        }
        sb.append(separator).append("\n");

        // 행 수 요약
        sb.append(truncated
            ? totalCount + "건 중 " + MAX_ROWS + "건 표시 (최대 " + MAX_ROWS + "행 제한)\n"
            : totalCount + "건\n");

        return sb.toString();
    }

    private String buildSeparator(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            sb.append("-".repeat(w + 2)).append("+");
        }
        return sb.toString();
    }

    private String pad(String s, int width) {
        if (s.length() >= width) return s;
        return s + " ".repeat(width - s.length());
    }
}
