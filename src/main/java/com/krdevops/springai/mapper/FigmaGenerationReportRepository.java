package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.ops.FigmaGenerationReport;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Plugin 실행 보고서의 불변 원문을 저장한다. */
@Slf4j
@Repository
public class FigmaGenerationReportRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public FigmaGenerationReportRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
            LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) {
            return;
        }
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_GENERATION_REPORT (
                REPORT_ID       VARCHAR(64) NOT NULL,
                SCREEN_ID       VARCHAR(64) NOT NULL,
                SCREEN_VERSION  INT NOT NULL,
                SUCCESS_YN      CHAR(1) NOT NULL,
                REPORT_JSON     LONGTEXT NOT NULL,
                CREATED_AT      DATETIME DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (REPORT_ID),
                KEY IDX_FIGMA_REPORT_SCREEN (SCREEN_ID, SCREEN_VERSION)
            )
            """);
        log.info("AI_FIGMA_GENERATION_REPORT 테이블 초기화 완료");
    }

    public FigmaGenerationReport saveImmutable(FigmaGenerationReport report) {
        Optional<FigmaGenerationReport> existing = findById(report.reportId());
        if (existing.isPresent()) {
            if (!existing.get().equals(report)) {
                throw new IllegalStateException(
                        "같은 reportId에 다른 생성 보고서가 이미 저장되어 있습니다: " + report.reportId());
            }
            return existing.get();
        }
        jdbcTemplate.update("""
            INSERT INTO AI_FIGMA_GENERATION_REPORT
                (REPORT_ID, SCREEN_ID, SCREEN_VERSION, SUCCESS_YN, REPORT_JSON)
            VALUES (?, ?, ?, ?, ?)
            """, report.reportId(), report.screenId(), report.screenVersion(),
                report.success() ? "Y" : "N", toJson(report));
        return report;
    }

    public Optional<FigmaGenerationReport> findById(String reportId) {
        List<String> values = jdbcTemplate.queryForList("""
            SELECT REPORT_JSON FROM AI_FIGMA_GENERATION_REPORT WHERE REPORT_ID = ?
            """, String.class, reportId);
        return values.isEmpty() ? Optional.empty() : Optional.of(fromJson(values.get(0)));
    }

    public List<FigmaGenerationReport> findByScreen(String screenId) {
        return jdbcTemplate.queryForList("""
            SELECT REPORT_JSON FROM AI_FIGMA_GENERATION_REPORT
             WHERE SCREEN_ID = ? ORDER BY CREATED_AT DESC
            """, String.class, screenId).stream().map(this::fromJson).toList();
    }

    public List<FigmaGenerationReport> findAll() {
        return jdbcTemplate.queryForList("""
            SELECT REPORT_JSON FROM AI_FIGMA_GENERATION_REPORT ORDER BY CREATED_AT DESC
            """, String.class).stream().map(this::fromJson).toList();
    }

    private String toJson(FigmaGenerationReport report) {
        try {
            return objectMapper.writeValueAsString(report);
        } catch (Exception exception) {
            throw new IllegalStateException("Figma 생성 보고서 직렬화 실패", exception);
        }
    }

    private FigmaGenerationReport fromJson(String json) {
        try {
            return objectMapper.readValue(json, FigmaGenerationReport.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Figma 생성 보고서 역직렬화 실패", exception);
        }
    }
}
