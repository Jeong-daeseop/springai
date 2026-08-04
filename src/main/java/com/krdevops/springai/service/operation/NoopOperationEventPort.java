package com.krdevops.springai.service.operation;

import com.krdevops.springai.model.operation.OperationEvent;
import java.util.List;

public final class NoopOperationEventPort implements OperationEventPort {
    public void append(OperationEvent event) { }
    public List<OperationEvent> findByOperation(String operationId) { return List.of(); }
}
