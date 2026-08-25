package com.krdevops.springai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.controlplane.ReadinessComparisonRecord;
import com.krdevops.springai.model.controlplane.ReadinessComparisonSummary;
import com.krdevops.springai.model.controlplane.ReadinessComparisonReport;
import com.krdevops.springai.service.controlplane.ReadinessComparisonPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class ReadinessComparisonRepository implements ReadinessComparisonPort {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ReadinessComparisonRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void append(ReadinessComparisonRecord record) {
        jdbc.update("""
                INSERT INTO AI_GENERATION_READINESS_COMPARISON
                (COMPARISON_ID, OPERATION_ID, SOURCE_TYPE, LEGACY_READY, COMMON_READY, MATCHED,
                 MISMATCH_REASON, LEGACY_FAILED_GATES_JSON, COMMON_FAILED_GATES_JSON,
                 COMMON_MISSING_GATES_JSON, CREATED_AT)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, record.comparisonId(), record.operationId(), record.sourceType().name(),
                record.legacyReady(), record.commonReady(), record.matched(), record.mismatchReason(),
                json(record.legacyFailedGateNames()), json(record.commonFailedGateNames()),
                json(record.commonMissingGateNames()), Timestamp.from(record.createdAt()));
    }

    @Override
    public Optional<ReadinessComparisonSummary> summary() {
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM AI_GENERATION_READINESS_COMPARISON", Long.class);
        Long mismatch = jdbc.queryForObject(
                "SELECT COUNT(*) FROM AI_GENERATION_READINESS_COMPARISON WHERE MATCHED = FALSE", Long.class);
        Map<String, Long> reasons = new LinkedHashMap<>();
        jdbc.query("""
                SELECT MISMATCH_REASON, COUNT(*) AS CNT
                  FROM AI_GENERATION_READINESS_COMPARISON
                 WHERE MATCHED = FALSE
                 GROUP BY MISMATCH_REASON
                """, (RowCallbackHandler) rs ->
                reasons.put(rs.getString("MISMATCH_REASON"), rs.getLong("CNT")));
        return Optional.of(new ReadinessComparisonSummary(
                total == null ? 0 : total, mismatch == null ? 0 : mismatch, reasons));
    }

    @Override
    public Optional<ReadinessComparisonReport> report(Instant from, Instant to) {
        Timestamp start = Timestamp.from(from);
        Timestamp end = Timestamp.from(to);
        Long total = count("SELECT COUNT(*) FROM AI_GENERATION_READINESS_COMPARISON WHERE CREATED_AT >= ? AND CREATED_AT < ?", start, end);
        Long mismatch = count("SELECT COUNT(*) FROM AI_GENERATION_READINESS_COMPARISON WHERE CREATED_AT >= ? AND CREATED_AT < ? AND MATCHED = FALSE", start, end);
        Map<String, Long> reasons = grouped("MISMATCH_REASON", true, start, end);
        Map<String, Long> sources = grouped("SOURCE_TYPE", false, start, end);
        Map<String, Long> days = new LinkedHashMap<>();
        jdbc.query("""
                SELECT DATE_FORMAT(CREATED_AT, '%Y-%m-%d') AS GROUP_KEY, COUNT(*) AS CNT
                  FROM AI_GENERATION_READINESS_COMPARISON
                 WHERE CREATED_AT >= ? AND CREATED_AT < ?
                 GROUP BY DATE_FORMAT(CREATED_AT, '%Y-%m-%d') ORDER BY GROUP_KEY
                """, (RowCallbackHandler) rs -> days.put(rs.getString("GROUP_KEY"), rs.getLong("CNT")), start, end);
        long totalValue = total == null ? 0 : total;
        long mismatchValue = mismatch == null ? 0 : mismatch;
        double rate = totalValue == 0 ? 0.0 : (double) mismatchValue / totalValue;
        return Optional.of(new ReadinessComparisonReport(from, to, totalValue, mismatchValue, rate,
                reasons, sources, days));
    }

    private Long count(String sql, Timestamp from, Timestamp to) {
        return jdbc.queryForObject(sql, Long.class, from, to);
    }

    private Map<String, Long> grouped(String column, boolean mismatchesOnly, Timestamp from, Timestamp to) {
        if (!"MISMATCH_REASON".equals(column) && !"SOURCE_TYPE".equals(column)) {
            throw new IllegalArgumentException("지원하지 않는 비교 집계 열입니다.");
        }
        String sql = "SELECT " + column + " AS GROUP_KEY, COUNT(*) AS CNT "
                + "FROM AI_GENERATION_READINESS_COMPARISON WHERE CREATED_AT >= ? AND CREATED_AT < ?"
                + (mismatchesOnly ? " AND MATCHED = FALSE" : "")
                + " GROUP BY " + column + " ORDER BY " + column;
        Map<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs -> result.put(rs.getString("GROUP_KEY"), rs.getLong("CNT")), from, to);
        return result;
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Readiness 비교 Gate 목록 직렬화 실패", exception);
        }
    }
}
