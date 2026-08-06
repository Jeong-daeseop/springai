package com.krdevops.springai.config;

import com.zaxxer.hikari.HikariDataSource;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.krdevops.springai.service.resilience.ExternalDependency;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

@Configuration
public class OperationalDataSourceConfig {

    @Bean("physicalDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource physicalDataSource(DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean("dataSource")
    @Primary
    DataSource dataSource(@Qualifier("physicalDataSource") HikariDataSource delegate,
                          ExternalCallGuard guard) {
        return new CircuitBreakingDataSource(delegate, guard);
    }

    static final class CircuitBreakingDataSource implements DataSource {
        private final DataSource delegate;
        private final ExternalCallGuard guard;

        CircuitBreakingDataSource(DataSource delegate, ExternalCallGuard guard) {
            this.delegate = delegate;
            this.guard = guard;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return guarded(delegate::getConnection);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return guarded(() -> delegate.getConnection(username, password));
        }

        private Connection guarded(SqlConnectionSupplier supplier) throws SQLException {
            try {
                return guard.execute(ExternalDependency.MYSQL, supplier::get);
            } catch (RuntimeException e) {
                Throwable cause = e.getCause();
                if (cause instanceof SQLException sqlException) throw sqlException;
                throw new SQLException(e.getMessage(), e);
            }
        }

        @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
        @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
        @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
        @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
        @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) return iface.cast(this);
            return delegate.unwrap(iface);
        }
        @Override public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }

        @FunctionalInterface
        private interface SqlConnectionSupplier { Connection get() throws SQLException; }
    }
}
