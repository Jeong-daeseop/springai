package com.krdevops.springai.mapper;

import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.model.designsystem.FigmaReviewEvent;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** R1-023/024: Preview 검토·승인 이력과 Library Publish·Registry 동기화 이력 저장·조회. */
@Slf4j
@Repository
public class FigmaReviewHistoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final LegacyRepositoryDdlProperties ddlProperties;

    public FigmaReviewHistoryRepository(JdbcTemplate jdbcTemplate, LegacyRepositoryDdlProperties ddlProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.ddlProperties = ddlProperties;
    }

    @PostConstruct
    public void createTableIfNotExists() {
        if (!ddlProperties.isLegacyRepositoryDdlEnabled()) {
            return;
        }
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS AI_FIGMA_REVIEW_HISTORY (
                EVENT_ID       VARCHAR(64) PRIMARY KEY,
                TARGET_TYPE    VARCHAR(32) NOT NULL,
                TARGET_ID      VARCHAR(64) NOT NULL,
                TARGET_VERSION VARCHAR(32) NOT NULL,
                EVENT_TYPE     VARCHAR(32) NOT NULL,
                EVENT_STATUS   VARCHAR(64),
                ACTOR          VARCHAR(128),
                COMMENT_TEXT   VARCHAR(2000),
                OCCURRED_AT    DATETIME DEFAULT CURRENT_TIMESTAMP,
                KEY IDX_FIGMA_REVIEW_HISTORY_TARGET (TARGET_TYPE, TARGET_ID, TARGET_VERSION)
            )
            """);
        log.info("AI_FIGMA_REVIEW_HISTORY 테이블 초기화 완료");
    }

    public void save(FigmaReviewEvent event) {
        jdbcTemplate.update("""
            INSERT INTO AI_FIGMA_REVIEW_HISTORY
                (EVENT_ID, TARGET_TYPE, TARGET_ID, TARGET_VERSION, EVENT_TYPE, EVENT_STATUS, ACTOR, COMMENT_TEXT, OCCURRED_AT)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                event.id(), event.targetType().name(), event.targetId(), event.targetVersion(),
                event.eventType().name(), event.status(), event.actor(), event.comment(), event.occurredAt());
    }

    public List<FigmaReviewEvent> findByTarget(
            FigmaReviewEvent.TargetType targetType, String targetId, String targetVersion) {
        return jdbcTemplate.query("""
            SELECT * FROM AI_FIGMA_REVIEW_HISTORY
             WHERE TARGET_TYPE = ? AND TARGET_ID = ? AND TARGET_VERSION = ?
             ORDER BY OCCURRED_AT ASC
            """,
                (rs, rowNum) -> new FigmaReviewEvent(
                        rs.getString("EVENT_ID"),
                        FigmaReviewEvent.TargetType.valueOf(rs.getString("TARGET_TYPE")),
                        rs.getString("TARGET_ID"),
                        rs.getString("TARGET_VERSION"),
                        FigmaReviewEvent.EventType.valueOf(rs.getString("EVENT_TYPE")),
                        rs.getString("EVENT_STATUS"),
                        rs.getString("ACTOR"),
                        rs.getString("COMMENT_TEXT"),
                        rs.getTimestamp("OCCURRED_AT") == null
                                ? null : rs.getTimestamp("OCCURRED_AT").toLocalDateTime()),
                targetType.name(), targetId, targetVersion);
    }

    /** R8-024: Preview 검토·반려 횟수 운영 지표. */
    public long countByEventType(FigmaReviewEvent.EventType eventType) {
        Long count = jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM AI_FIGMA_REVIEW_HISTORY WHERE EVENT_TYPE = ?
            """, Long.class, eventType.name());
        return count == null ? 0 : count;
    }
}
