package com.krdevops.springai.mapper;

import com.krdevops.springai.model.operation.OperationEvent;
import com.krdevops.springai.service.operation.OperationEventPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class OperationEventRepository implements OperationEventPort {
    private final JdbcTemplate jdbc;
    public OperationEventRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void append(OperationEvent e) {
        jdbc.update("""
            INSERT INTO AI_OPERATION_EVENT
            (EVENT_ID, OPERATION_ID, OPERATION_TYPE, REVISION, FROM_STATUS, TO_STATUS,
             EVENT_TYPE, ACTOR, CORRELATION_ID, PAYLOAD_HASH, OCCURRED_AT)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            e.eventId(), e.operationId(), e.operationType(), e.revision(), e.fromStatus(),
            e.toStatus(), e.eventType(), e.actor(), e.correlationId(), e.payloadHash(),
            Timestamp.from(e.occurredAt()));
    }
    public List<OperationEvent> findByOperation(String operationId) {
        return jdbc.query("SELECT * FROM AI_OPERATION_EVENT WHERE OPERATION_ID=? ORDER BY OCCURRED_AT, EVENT_ID",
            (rs, n) -> new OperationEvent(rs.getString("EVENT_ID"), rs.getString("OPERATION_ID"),
                rs.getString("OPERATION_TYPE"), rs.getInt("REVISION"), rs.getString("FROM_STATUS"),
                rs.getString("TO_STATUS"), rs.getString("EVENT_TYPE"), rs.getString("ACTOR"),
                rs.getString("CORRELATION_ID"), rs.getString("PAYLOAD_HASH"),
                rs.getTimestamp("OCCURRED_AT").toInstant()), operationId);
    }
}
