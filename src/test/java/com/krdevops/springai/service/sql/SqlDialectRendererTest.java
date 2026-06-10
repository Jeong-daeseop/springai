package com.krdevops.springai.service.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlDialectRendererTest {

    private final SqlDialectRenderer mysql = new SqlDialectRenderer(DbDialect.MYSQL_MARIADB);
    private final SqlDialectRenderer oracle = new SqlDialectRenderer(DbDialect.ORACLE);

    // --- now() ---

    @Test
    void MySQL_now_NOW() {
        assertThat(mysql.now()).isEqualTo("NOW()");
    }

    @Test
    void Oracle_now_SYSDATE() {
        assertThat(oracle.now()).isEqualTo("SYSDATE");
    }

    // --- limit() ---

    @Test
    void MySQL_limit_LIMIT() {
        assertThat(mysql.limit(50)).isEqualTo("LIMIT 50");
    }

    @Test
    void Oracle_limit_FETCH_FIRST_ROWS_ONLY() {
        assertThat(oracle.limit(50)).isEqualTo("FETCH FIRST 50 ROWS ONLY");
    }

    // --- roleCodeMaxExpr() ---

    @Test
    void MySQL_roleCodeMaxExpr_CAST_SUBSTRING() {
        assertThat(mysql.roleCodeMaxExpr()).contains("CAST").contains("SUBSTRING").contains("UNSIGNED");
    }

    @Test
    void Oracle_roleCodeMaxExpr_TO_NUMBER_REGEXP_SUBSTR() {
        assertThat(oracle.roleCodeMaxExpr()).contains("TO_NUMBER").contains("REGEXP_SUBSTR");
    }

    // --- wrapLimit() ---

    @Test
    void MySQL_wrapLimit_LIMIT_suffix() {
        String result = mysql.wrapLimit("SELECT * FROM T", 50);
        assertThat(result).endsWith("LIMIT 50");
        assertThat(result).doesNotContain("ROWNUM");
    }

    @Test
    void Oracle_wrapLimit_ROWNUM() {
        String result = oracle.wrapLimit("SELECT * FROM T", 50);
        assertThat(result).contains("ROWNUM").contains("<= 50");
        assertThat(result).doesNotContain("LIMIT");
    }
}
