package com.krdevops.springai.service.auth;

import com.krdevops.springai.service.sql.DbDialect;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public int findNextRoleNum(SqlDialectRenderer renderer) {
        String sql = "SELECT " + renderer.roleCodeMaxExpr()
                + " FROM COMTNROLEINFO WHERE ROLE_CODE LIKE 'web-%'";
        Integer max = jdbcTemplate.queryForObject(sql, Integer.class);
        return (max != null ? max : 0) + 1;
    }

    public List<Map<String, Object>> searchPrograms(String keyword, SqlDialectRenderer renderer) {
        if (keyword == null || keyword.isBlank()) {
            if (renderer.getDialect() == DbDialect.MYSQL_MARIADB) {
                return jdbcTemplate.queryForList(
                        "SELECT PROGRM_FILE_NM, PROGRM_KOREAN_NM, URL FROM COMTNPROGRMLIST ORDER BY PROGRM_FILE_NM LIMIT 50");
            } else {
                return jdbcTemplate.queryForList(
                        "SELECT PROGRM_FILE_NM, PROGRM_KOREAN_NM, URL FROM COMTNPROGRMLIST ORDER BY PROGRM_FILE_NM FETCH FIRST 50 ROWS ONLY");
            }
        }
        String like = "%" + keyword + "%";
        if (renderer.getDialect() == DbDialect.MYSQL_MARIADB) {
            return jdbcTemplate.queryForList(
                    "SELECT PROGRM_FILE_NM, PROGRM_KOREAN_NM, URL FROM COMTNPROGRMLIST " +
                    "WHERE PROGRM_FILE_NM LIKE ? OR PROGRM_KOREAN_NM LIKE ? OR URL LIKE ? " +
                    "ORDER BY PROGRM_FILE_NM LIMIT 50",
                    like, like, like);
        } else {
            return jdbcTemplate.queryForList(
                    "SELECT * FROM (SELECT PROGRM_FILE_NM, PROGRM_KOREAN_NM, URL FROM COMTNPROGRMLIST " +
                    "WHERE PROGRM_FILE_NM LIKE ? OR PROGRM_KOREAN_NM LIKE ? OR URL LIKE ? " +
                    "ORDER BY PROGRM_FILE_NM) WHERE ROWNUM <= 50",
                    like, like, like);
        }
    }
}
