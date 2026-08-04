package com.krdevops.springai.model.operation;

import java.time.Instant;

/** DB 기반 작업 범위 잠금과 fencing token. */
public record OperationLock(String lockKey, String operationId, String ownerId,
                            long fencingToken, Instant expiresAt) { }
