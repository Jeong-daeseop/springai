package com.krdevops.springai.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FigmaGenerationReportRepositoryIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final FigmaGenerationReportRepository repository =
            new FigmaGenerationReportRepository(
                    jdbcTemplate, new ObjectMapper().findAndRegisterModules());

    @BeforeEach
    void setUp() {
        repository.createTableIfNotExists();
    }

    @Test
    void 보고서는불변멱등저장되고화면별로조회된다() {
        String reportId = "report-" + UUID.randomUUID();
        FigmaGenerationReport report = report(reportId, true);
        try {
            repository.saveImmutable(report);
            repository.saveImmutable(report);

            assertThat(repository.findByScreen(report.screenId())).containsExactly(report);
            assertThat(repository.findById(reportId)).contains(report);
        } finally {
            jdbcTemplate.update("DELETE FROM AI_FIGMA_GENERATION_REPORT WHERE REPORT_ID = ?", reportId);
        }
    }

    @Test
    void 같은reportId의다른내용은거부한다() {
        String reportId = "report-" + UUID.randomUUID();
        try {
            repository.saveImmutable(report(reportId, true));

            assertThatThrownBy(() -> repository.saveImmutable(report(reportId, false)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("다른 생성 보고서");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_FIGMA_GENERATION_REPORT WHERE REPORT_ID = ?", reportId);
        }
    }

    private FigmaGenerationReport report(String id, boolean success) {
        Instant started = Instant.parse("2026-07-27T00:00:00Z");
        return new FigmaGenerationReport(
                id, success ? FigmaGenerationReport.Status.SUCCESS
                        : FigmaGenerationReport.Status.FAILED,
                null, "users-list-" + id, 1, FigmaSyncMode.MERGE,
                started, started.plusSeconds(1), success,
                3, 1, 0, 0, List.of(), List.of());
    }
}
