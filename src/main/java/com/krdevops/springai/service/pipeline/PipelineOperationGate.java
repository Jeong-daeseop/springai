package com.krdevops.springai.service.pipeline;

import org.springframework.stereotype.Service;
import com.krdevops.springai.service.observability.PipelineMetricsCollector;

/** 외부 요청이 실행 facade에 도달하기 전 operation 조회와 권한 검증을 묶는다. */
@Service
public class PipelineOperationGate {
    private final PipelineApiOperationCatalog catalog;
    private final PipelineActionAuthorization authorization;
    private final PipelineMetricsCollector metrics;
    private final PipelineOperationAuditService audit;

    public PipelineOperationGate(PipelineApiOperationCatalog catalog,
                                 PipelineActionAuthorization authorization,
                                 PipelineMetricsCollector metrics,
                                 PipelineOperationAuditService audit) {
        this.catalog = catalog;
        this.authorization = authorization;
        this.metrics = metrics;
        this.audit = audit;
    }

    public PipelineApiOperationCatalog.Operation authorize(
            String operationName,
            PipelineActionAuthorization.AuthorizationContext context) {
        try {
            PipelineApiOperationCatalog.Operation operation = catalog.require(operationName);
            authorization.requireOperation(operation, context);
            metrics.increment("pipeline_operation_authorized");
            audit.record(operation.name(), operation.risk(), true);
            return operation;
        } catch (RuntimeException exception) {
            metrics.increment("pipeline_operation_denied");
            if (catalog.contains(operationName)) {
                var denied = catalog.require(operationName);
                audit.record(denied.name(), denied.risk(), false);
            } else if (operationName != null && !operationName.isBlank()) {
                audit.record(operationName, "UNKNOWN", false);
            }
            throw exception;
        }
    }
}
