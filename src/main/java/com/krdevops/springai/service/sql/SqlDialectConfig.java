package com.krdevops.springai.service.sql;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SqlDialectProperties.class)
public class SqlDialectConfig {

    @Bean
    public SqlDialectRenderer sqlDialectRenderer(DbDialectResolver resolver) {
        return new SqlDialectRenderer(resolver.resolve());
    }
}
