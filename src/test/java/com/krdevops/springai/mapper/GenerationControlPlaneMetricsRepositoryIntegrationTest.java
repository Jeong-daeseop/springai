package com.krdevops.springai.mapper;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationControlPlaneMetricsRepositoryIntegrationTest {

    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01")));

    @Test
    void Thymeleaf_Row수와_실제_Operation수를_분리하고_현재상태_합계가_Operation수와_일치한다() {
        var metrics = new GenerationControlPlaneMetricsRepository(jdbc).load();
        var thymeleaf = metrics.thymeleafMigration();

        assertThat(thymeleaf.totalRows()).isPositive();
        assertThat(thymeleaf.distinctOperations()).isPositive().isLessThanOrEqualTo(thymeleaf.totalRows());
        assertThat(thymeleaf.allRevisionStatusCounts().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(thymeleaf.totalRows());
        assertThat(thymeleaf.latestStatusCounts().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(thymeleaf.distinctOperations());
    }

    @Test
    void CRUD_Snapshot_Row수와_Operation수를_실제_DB값과_동일하게_집계한다() {
        var crud = new GenerationControlPlaneMetricsRepository(jdbc).load().crud();
        Long expectedRows = jdbc.queryForObject("SELECT COUNT(*) FROM AI_CRUD_GENERATION_SNAPSHOT", Long.class);
        Long expectedOperations = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT OPERATION_ID) FROM AI_CRUD_GENERATION_SNAPSHOT", Long.class);

        assertThat(crud.totalRows()).isEqualTo(expectedRows);
        assertThat(crud.distinctOperations()).isEqualTo(expectedOperations);
    }
}
