package com.krdevops.springai.service.sql;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DB 방언 설정. application.yaml: app.sql.dialect
 * 지원값: mysql_mariadb, oracle, auto
 */
@ConfigurationProperties(prefix = "app.sql")
public class SqlDialectProperties {

    private String dialect = "mysql_mariadb";

    public String getDialect() {
        return dialect;
    }

    public void setDialect(String dialect) {
        this.dialect = dialect;
    }
}
