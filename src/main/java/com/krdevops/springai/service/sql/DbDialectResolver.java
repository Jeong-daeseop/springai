package com.krdevops.springai.service.sql;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Slf4j
@Component
@RequiredArgsConstructor
public class DbDialectResolver {

    private final SqlDialectProperties properties;
    private final DataSource dataSource;

    public DbDialect resolve() {
        String configured = properties.getDialect();
        if ("auto".equalsIgnoreCase(configured)) {
            return detectFromMetadata();
        }
        return parseConfigured(configured);
    }

    private DbDialect parseConfigured(String value) {
        if (value == null || value.isBlank()) {
            return DbDialect.MYSQL_MARIADB;
        }
        return switch (value.trim().toLowerCase()) {
            case "oracle" -> DbDialect.ORACLE;
            default -> DbDialect.MYSQL_MARIADB;
        };
    }

    private DbDialect detectFromMetadata() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String productName = meta.getDatabaseProductName();
            if (productName != null && productName.toLowerCase().contains("oracle")) {
                log.info("DB Dialect 자동 감지: ORACLE (productName={})", productName);
                return DbDialect.ORACLE;
            }
            log.info("DB Dialect 자동 감지: MYSQL_MARIADB (productName={})", productName);
            return DbDialect.MYSQL_MARIADB;
        } catch (SQLException e) {
            log.warn("DB Dialect 자동 감지 실패 — MYSQL_MARIADB 기본값 사용: {}", e.getMessage());
            return DbDialect.MYSQL_MARIADB;
        }
    }
}
