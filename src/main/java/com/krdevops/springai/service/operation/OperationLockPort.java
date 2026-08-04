package com.krdevops.springai.service.operation;

import com.krdevops.springai.model.operation.OperationLock;
import java.time.Duration;
import java.util.Optional;

public interface OperationLockPort {
    Optional<OperationLock> acquire(String lockKey, String operationId, String ownerId, Duration ttl);
    void release(OperationLock lock);
}
