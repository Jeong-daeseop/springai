package com.krdevops.springai.mapper;

import com.krdevops.springai.model.operation.OperationLock;
import com.krdevops.springai.service.operation.OperationLockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class OperationLockRepository implements OperationLockPort {
    private final JdbcTemplate jdbc;
    public OperationLockRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional
    public Optional<OperationLock> acquire(String key, String operationId, String owner, Duration ttl) {
        List<OperationLock> rows = jdbc.query("SELECT * FROM AI_OPERATION_LOCK WHERE LOCK_KEY=? FOR UPDATE",
            (rs, n) -> new OperationLock(rs.getString("LOCK_KEY"), rs.getString("OPERATION_ID"),
                rs.getString("OWNER_ID"), rs.getLong("FENCING_TOKEN"), rs.getTimestamp("EXPIRES_AT").toInstant()), key);
        Instant expiry = Instant.now().plus(ttl);
        if (rows.isEmpty()) {
            jdbc.update("INSERT INTO AI_OPERATION_LOCK (LOCK_KEY, OPERATION_ID, OWNER_ID, FENCING_TOKEN, EXPIRES_AT) VALUES (?, ?, ?, 1, ?)",
                key, operationId, owner, Timestamp.from(expiry));
            return Optional.of(new OperationLock(key, operationId, owner, 1, expiry));
        }
        OperationLock current = rows.get(0);
        if (current.expiresAt().isAfter(Instant.now()) && !current.ownerId().equals(owner)) return Optional.empty();
        long token = current.fencingToken() + 1;
        jdbc.update("UPDATE AI_OPERATION_LOCK SET OPERATION_ID=?, OWNER_ID=?, FENCING_TOKEN=?, EXPIRES_AT=? WHERE LOCK_KEY=?",
            operationId, owner, token, Timestamp.from(expiry), key);
        return Optional.of(new OperationLock(key, operationId, owner, token, expiry));
    }
    @Transactional
    public void release(OperationLock lock) {
        jdbc.update("DELETE FROM AI_OPERATION_LOCK WHERE LOCK_KEY=? AND OWNER_ID=? AND FENCING_TOKEN=?",
            lock.lockKey(), lock.ownerId(), lock.fencingToken());
    }
}
