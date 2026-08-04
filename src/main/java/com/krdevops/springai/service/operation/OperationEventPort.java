package com.krdevops.springai.service.operation;

import com.krdevops.springai.model.operation.OperationEvent;
import java.util.List;

public interface OperationEventPort {
    void append(OperationEvent event);
    List<OperationEvent> findByOperation(String operationId);
}
