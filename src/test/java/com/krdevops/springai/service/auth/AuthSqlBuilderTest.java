package com.krdevops.springai.service.auth;

import com.krdevops.springai.model.AuthRegistrationSpec;
import com.krdevops.springai.model.SqlPlan;
import com.krdevops.springai.service.sql.DbDialect;
import com.krdevops.springai.service.sql.SqlDialectRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSqlBuilderTest {

    private static final AuthRegistrationSpec SPEC =
            new AuthRegistrationSpec("/emp/employer", "직원관리", "emp");

    // --- Oracle ---

    @Test
    void Oracle_COMTNROLEINFO_INSERT에_SYSDATE_포함() {
        SqlPlan plan = buildWith(DbDialect.ORACLE);

        String roleInfoSql = plan.statements().get(0);
        assertThat(roleInfoSql).contains("COMTNROLEINFO");
        assertThat(roleInfoSql).contains("SYSDATE");
        assertThat(roleInfoSql).doesNotContain("NOW()");
    }

    @Test
    void Oracle_COMTNROLEINFO_CREAT_DT와_MDFCN_DT에_SYSDATE_2회() {
        SqlPlan plan = buildWith(DbDialect.ORACLE);

        String roleInfoSql = plan.statements().get(0);
        assertThat(countOccurrences(roleInfoSql, "SYSDATE")).isEqualTo(2);
    }

    @Test
    void Oracle_COMTNAUTHORROLERELATE_INSERT에_SYSDATE_포함() {
        SqlPlan plan = buildWith(DbDialect.ORACLE);

        String relateSql = plan.statements().get(1);
        assertThat(relateSql).contains("COMTNAUTHORROLERELATE");
        assertThat(relateSql).contains("SYSDATE");
        assertThat(relateSql).doesNotContain("NOW()");
    }

    @Test
    void Oracle_인라인주석_SQL에_SYSDATE_포함() {
        SqlPlan plan = buildWith(DbDialect.ORACLE);

        // 3번째 statement는 주석, 4번째는 ROLE_USER 인라인 SQL
        String commentSql = plan.statements().get(3);
        assertThat(commentSql).contains("SYSDATE");
        assertThat(commentSql).doesNotContain("NOW()");
    }

    // --- MySQL/MariaDB ---

    @Test
    void MySQL_COMTNROLEINFO_INSERT에_NOW_포함() {
        SqlPlan plan = buildWith(DbDialect.MYSQL_MARIADB);

        String roleInfoSql = plan.statements().get(0);
        assertThat(roleInfoSql).contains("COMTNROLEINFO");
        assertThat(roleInfoSql).contains("NOW()");
        assertThat(roleInfoSql).doesNotContain("SYSDATE");
    }

    @Test
    void MySQL_COMTNAUTHORROLERELATE_INSERT에_NOW_포함() {
        SqlPlan plan = buildWith(DbDialect.MYSQL_MARIADB);

        String relateSql = plan.statements().get(1);
        assertThat(relateSql).contains("COMTNAUTHORROLERELATE");
        assertThat(relateSql).contains("NOW()");
        assertThat(relateSql).doesNotContain("SYSDATE");
    }

    // --- helpers ---

    private SqlPlan buildWith(DbDialect dialect) {
        SqlDialectRenderer renderer = new SqlDialectRenderer(dialect);
        return new AuthSqlBuilder(renderer).build(SPEC, 1);
    }

    private int countOccurrences(String text, String target) {
        int count = 0, idx = 0;
        while ((idx = text.indexOf(target, idx)) != -1) {
            count++;
            idx += target.length();
        }
        return count;
    }
}
