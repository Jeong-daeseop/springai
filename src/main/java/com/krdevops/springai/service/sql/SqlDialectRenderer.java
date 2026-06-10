package com.krdevops.springai.service.sql;

public class SqlDialectRenderer {

    private final DbDialect dialect;

    public SqlDialectRenderer(DbDialect dialect) {
        this.dialect = dialect;
    }

    public String limit(int n) {
        return switch (dialect) {
            case MYSQL_MARIADB -> "LIMIT " + n;
            case ORACLE -> "FETCH FIRST " + n + " ROWS ONLY";
        };
    }

    public String now() {
        return switch (dialect) {
            case MYSQL_MARIADB -> "NOW()";
            case ORACLE -> "SYSDATE";
        };
    }

    public String roleCodeMaxExpr() {
        return switch (dialect) {
            case MYSQL_MARIADB -> "MAX(CAST(SUBSTRING(ROLE_CODE, 5) AS UNSIGNED))";
            case ORACLE -> "MAX(TO_NUMBER(REGEXP_SUBSTR(ROLE_CODE, '[0-9]+$')))";
        };
    }

    public String wrapLimit(String innerSql, int n) {
        return switch (dialect) {
            case MYSQL_MARIADB -> innerSql + " " + limit(n);
            case ORACLE -> "SELECT * FROM (" + innerSql + ") WHERE ROWNUM <= " + n;
        };
    }

    public DbDialect getDialect() {
        return dialect;
    }
}
