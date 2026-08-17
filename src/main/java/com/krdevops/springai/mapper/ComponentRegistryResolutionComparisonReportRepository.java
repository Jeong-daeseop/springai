package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentRegistryResolutionComparisonReport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ComponentRegistryResolutionComparisonReportRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ComponentRegistryResolutionComparisonReportRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    public void save(ComponentRegistryResolutionComparisonReport report) {
        try {
            jdbcTemplate.update("""
                INSERT INTO AI_COMPONENT_REGISTRY_RESOLUTION_REPORT
                  (REPORT_ID, PROFILE_ID, LEGACY_REGISTRY_VERSION, RESOLVED_REGISTRY_VERSION,
                   COMPARED_AT, IDENTICAL, REPORT_JSON)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, report.reportId(), report.profileId(), report.legacyRegistryVersion(),
                    report.resolvedRegistryVersion(), report.comparedAt(), report.identical(),
                    objectMapper.writeValueAsString(report));
        } catch (Exception exception) {
            throw new IllegalStateException("Registry 비교 Report 저장 실패", exception);
        }
    }

    public Optional<ComponentRegistryResolutionComparisonReport> find(String reportId) {
        return jdbcTemplate.query("SELECT REPORT_JSON FROM AI_COMPONENT_REGISTRY_RESOLUTION_REPORT WHERE REPORT_ID = ?",
                rs -> rs.next() ? Optional.of(fromJson(rs.getString(1))) : Optional.empty(), reportId);
    }

    private ComponentRegistryResolutionComparisonReport fromJson(String json) {
        try { return objectMapper.readValue(json, ComponentRegistryResolutionComparisonReport.class); }
        catch (Exception exception) { throw new IllegalStateException("Registry 비교 Report 조회 실패", exception); }
    }
}
