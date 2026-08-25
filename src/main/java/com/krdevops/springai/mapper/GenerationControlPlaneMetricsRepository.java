package com.krdevops.springai.mapper;

import com.krdevops.springai.model.controlplane.GenerationOperationsMetrics;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class GenerationControlPlaneMetricsRepository {

    private final JdbcTemplate jdbc;

    public GenerationControlPlaneMetricsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public GenerationOperationsMetrics load() {
        return new GenerationOperationsMetrics(Instant.now(), crud(), thymeleaf());
    }

    private GenerationOperationsMetrics.PipelineMetrics crud() {
        long snapshotRows = count("SELECT COUNT(*) FROM AI_CRUD_GENERATION_SNAPSHOT");
        long operations = count("SELECT COUNT(DISTINCT OPERATION_ID) FROM AI_CRUD_GENERATION_SNAPSHOT");
        Map<String, Long> latest = grouped("""
                SELECT a.STATUS AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                  FROM AI_GENERATION_OPERATION_AUDIT a
                  JOIN (SELECT OPERATION_ID, MAX(OPERATION_REVISION) AS MAX_REVISION
                          FROM AI_GENERATION_OPERATION_AUDIT GROUP BY OPERATION_ID) latest
                    ON latest.OPERATION_ID = a.OPERATION_ID
                   AND latest.MAX_REVISION = a.OPERATION_REVISION
                 GROUP BY a.STATUS ORDER BY a.STATUS
                """);
        return new GenerationOperationsMetrics.PipelineMetrics(snapshotRows, operations,
                grouped("""
                        SELECT STATUS AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_GENERATION_OPERATION_AUDIT GROUP BY STATUS ORDER BY STATUS
                        """), latest,
                grouped("""
                        SELECT COALESCE(CALLER_TYPE, 'UNKNOWN') AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_GENERATION_OPERATION_AUDIT
                         GROUP BY COALESCE(CALLER_TYPE, 'UNKNOWN') ORDER BY LABEL_VALUE
                        """),
                grouped("""
                        SELECT COALESCE(ACTOR_ID, 'UNKNOWN') AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_GENERATION_OPERATION_AUDIT
                         GROUP BY COALESCE(ACTOR_ID, 'UNKNOWN') ORDER BY LABEL_VALUE
                        """),
                grouped("""
                        SELECT COALESCE(ENVIRONMENT_NAME, 'UNKNOWN') AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_GENERATION_OPERATION_AUDIT
                         GROUP BY COALESCE(ENVIRONMENT_NAME, 'UNKNOWN') ORDER BY LABEL_VALUE
                        """),
                grouped("""
                        SELECT COALESCE(PROJECT_ROOT, 'UNKNOWN') AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_GENERATION_OPERATION_AUDIT
                         GROUP BY COALESCE(PROJECT_ROOT, 'UNKNOWN') ORDER BY LABEL_VALUE
                        """),
                grouped("""
                        SELECT COALESCE(SCREEN_ID, TABLE_NAME, 'UNKNOWN') AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_GENERATION_OPERATION_AUDIT
                         GROUP BY COALESCE(SCREEN_ID, TABLE_NAME, 'UNKNOWN') ORDER BY LABEL_VALUE
                        """));
    }

    private GenerationOperationsMetrics.PipelineMetrics thymeleaf() {
        return new GenerationOperationsMetrics.PipelineMetrics(
                count("SELECT COUNT(*) FROM AI_THYMELEAF_PROJECT_OPERATION"),
                count("SELECT COUNT(DISTINCT OPERATION_ID) FROM AI_THYMELEAF_PROJECT_OPERATION"),
                grouped("""
                        SELECT STATUS AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_THYMELEAF_PROJECT_OPERATION GROUP BY STATUS ORDER BY STATUS
                        """),
                grouped("""
                        SELECT o.STATUS AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_THYMELEAF_PROJECT_OPERATION o
                          JOIN (SELECT OPERATION_ID, MAX(REVISION) AS MAX_REVISION
                                  FROM AI_THYMELEAF_PROJECT_OPERATION GROUP BY OPERATION_ID) latest
                            ON latest.OPERATION_ID = o.OPERATION_ID AND latest.MAX_REVISION = o.REVISION
                         GROUP BY o.STATUS ORDER BY o.STATUS
                        """),
                Map.of("UNKNOWN", count("SELECT COUNT(DISTINCT OPERATION_ID) FROM AI_THYMELEAF_PROJECT_OPERATION")),
                grouped("""
                        SELECT COALESCE(ACTOR, 'UNKNOWN') AS LABEL_VALUE, COUNT(*) AS ITEM_COUNT
                          FROM AI_OPERATION_EVENT WHERE OPERATION_TYPE = 'THYMELEAF_PROJECT'
                         GROUP BY COALESCE(ACTOR, 'UNKNOWN') ORDER BY LABEL_VALUE
                        """),
                Map.of("UNKNOWN", count("SELECT COUNT(DISTINCT OPERATION_ID) FROM AI_THYMELEAF_PROJECT_OPERATION")),
                latestJsonValueCounts("$.projectRoot"), latestJsonValueCounts("$.bindingContract.screenId"));
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private Map<String, Long> grouped(String sql) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        jdbc.query(sql, (rs, rowNum) -> Map.entry(
                rs.getString("LABEL_VALUE"), rs.getLong("ITEM_COUNT")))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(result);
    }

    private Map<String, Long> latestJsonValueCounts(String jsonPath) {
        return grouped("""
                SELECT COALESCE(JSON_UNQUOTE(JSON_EXTRACT(o.SNAPSHOT_JSON, '%s')), 'UNKNOWN') AS LABEL_VALUE,
                       COUNT(*) AS ITEM_COUNT
                  FROM AI_THYMELEAF_PROJECT_OPERATION o
                  JOIN (SELECT OPERATION_ID, MAX(REVISION) AS MAX_REVISION
                          FROM AI_THYMELEAF_PROJECT_OPERATION GROUP BY OPERATION_ID) latest
                    ON latest.OPERATION_ID = o.OPERATION_ID AND latest.MAX_REVISION = o.REVISION
                 GROUP BY LABEL_VALUE ORDER BY LABEL_VALUE
                """.formatted(jsonPath));
    }
}
