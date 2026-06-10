package com.krdevops.springai.service.sql;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class DbDialectResolverTest {

    private DbDialectResolver resolverWith(String dialectConfig, String productName) throws SQLException {
        SqlDialectProperties props = new SqlDialectProperties();
        props.setDialect(dialectConfig);

        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);

        return new DbDialectResolver(props, dataSource);
    }

    @Test
    void mysql_mariadb_설정_MYSQL_MARIADB_반환() throws SQLException {
        DbDialectResolver resolver = resolverWith("mysql_mariadb", "MySQL");
        assertThat(resolver.resolve()).isEqualTo(DbDialect.MYSQL_MARIADB);
    }

    @Test
    void oracle_설정_ORACLE_반환() throws SQLException {
        DbDialectResolver resolver = resolverWith("oracle", "Oracle");
        assertThat(resolver.resolve()).isEqualTo(DbDialect.ORACLE);
    }

    @Test
    void auto_MySQL_productName_MYSQL_MARIADB_반환() throws SQLException {
        DbDialectResolver resolver = resolverWith("auto", "MySQL");
        assertThat(resolver.resolve()).isEqualTo(DbDialect.MYSQL_MARIADB);
    }

    @Test
    void auto_MariaDB_productName_MYSQL_MARIADB_반환() throws SQLException {
        DbDialectResolver resolver = resolverWith("auto", "MariaDB");
        assertThat(resolver.resolve()).isEqualTo(DbDialect.MYSQL_MARIADB);
    }

    @Test
    void auto_Oracle_productName_ORACLE_반환() throws SQLException {
        DbDialectResolver resolver = resolverWith("auto", "Oracle");
        assertThat(resolver.resolve()).isEqualTo(DbDialect.ORACLE);
    }

    @Test
    void auto_감지_실패_MYSQL_MARIADB_fallback() throws SQLException {
        SqlDialectProperties props = new SqlDialectProperties();
        props.setDialect("auto");

        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("connection refused"));

        DbDialectResolver resolver = new DbDialectResolver(props, dataSource);
        assertThat(resolver.resolve()).isEqualTo(DbDialect.MYSQL_MARIADB);
    }

    @Test
    void 알수없는_설정값_MYSQL_MARIADB_fallback() throws SQLException {
        DbDialectResolver resolver = resolverWith("unknown_db", "H2");
        assertThat(resolver.resolve()).isEqualTo(DbDialect.MYSQL_MARIADB);
    }
}
