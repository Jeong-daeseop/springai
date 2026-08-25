package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.GenerationOperation;
import com.krdevops.springai.model.controlplane.GenerationSourceType;
import com.krdevops.springai.model.controlplane.ReleaseReadiness;
import com.krdevops.springai.model.controlplane.ValidationEvidence;
import com.krdevops.springai.model.controlplane.GenerationOperationsMetrics;
import com.krdevops.springai.mapper.GenerationControlPlaneMetricsRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GenerationControlPlaneService {

    private final List<GenerationOperationAdapter> adapters;
    private final ReleaseReadinessEvaluator readinessEvaluator;
    private final GenerationControlPlaneMetricsRepository metricsRepository;

    public GenerationControlPlaneService(List<GenerationOperationAdapter> adapters,
                                         ReleaseReadinessEvaluator readinessEvaluator,
                                         GenerationControlPlaneMetricsRepository metricsRepository) {
        this.adapters = List.copyOf(adapters);
        this.readinessEvaluator = readinessEvaluator;
        this.metricsRepository = metricsRepository;
    }

    public Optional<GenerationOperation> find(String operationId, GenerationSourceType sourceType) {
        List<GenerationOperation> matches = adapters.stream()
                .filter(adapter -> sourceType == null || adapter.sourceType() == sourceType)
                .map(adapter -> adapter.find(operationId)).flatMap(Optional::stream).toList();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("GENERATION_OPERATION_SOURCE_TYPE_REQUIRED: " + operationId);
        }
        return matches.stream().findFirst();
    }

    public List<ValidationEvidence> evidence(String operationId, GenerationSourceType sourceType) {
        GenerationOperationAdapter adapter = requiredAdapter(operationId, sourceType);
        return adapter.evidence(operationId);
    }

    public ReleaseReadiness readiness(String operationId, GenerationSourceType sourceType) {
        GenerationOperationAdapter adapter = requiredAdapter(operationId, sourceType);
        GenerationOperation operation = adapter.find(operationId)
                .orElseThrow(() -> notFound(operationId));
        return readinessEvaluator.evaluate(operation, adapter.evidence(operationId));
    }

    public GenerationOperationsMetrics metrics() {
        return metricsRepository.load();
    }

    private GenerationOperationAdapter requiredAdapter(String operationId, GenerationSourceType sourceType) {
        List<GenerationOperationAdapter> matches = adapters.stream()
                .filter(adapter -> sourceType == null || adapter.sourceType() == sourceType)
                .filter(adapter -> adapter.find(operationId).isPresent()).toList();
        if (matches.isEmpty()) throw notFound(operationId);
        if (matches.size() > 1) {
            throw new IllegalArgumentException("GENERATION_OPERATION_SOURCE_TYPE_REQUIRED: " + operationId);
        }
        return matches.get(0);
    }

    private IllegalArgumentException notFound(String operationId) {
        return new IllegalArgumentException("GENERATION_OPERATION_NOT_FOUND: " + operationId);
    }
}
