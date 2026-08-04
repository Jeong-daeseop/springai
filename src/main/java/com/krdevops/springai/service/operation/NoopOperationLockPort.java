package com.krdevops.springai.service.operation;

import com.krdevops.springai.model.operation.OperationLock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class NoopOperationLockPort implements OperationLockPort {
    public Optional<OperationLock> acquire(String key, String operationId, String owner, Duration ttl) {
        return Optional.of(new OperationLock(key, operationId, owner, 0L, Instant.now().plus(ttl)));
    }
    public void release(OperationLock lock) { }
}
