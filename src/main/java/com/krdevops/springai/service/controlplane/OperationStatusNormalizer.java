package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.model.controlplane.GenerationOperationStatus;
import org.springframework.stereotype.Component;

@Component
public class OperationStatusNormalizer {
    public GenerationOperationStatus normalize(String sourceStatus) {
        if (sourceStatus == null || sourceStatus.isBlank()) return GenerationOperationStatus.UNKNOWN;
        return switch (sourceStatus) {
            case "PREVIEW_READY" -> GenerationOperationStatus.PREVIEW_READY;
            case "APPROVED" -> GenerationOperationStatus.APPROVED;
            case "APPLIED" -> GenerationOperationStatus.APPLIED;
            case "VALIDATED" -> GenerationOperationStatus.VALIDATED;
            case "CONFLICT" -> GenerationOperationStatus.CONFLICT;
            case "FAILED" -> GenerationOperationStatus.FAILED;
            case "REJECTED" -> GenerationOperationStatus.REJECTED;
            case "ANALYZED", "CONTRACT_READY" -> GenerationOperationStatus.APPROVAL_REQUIRED;
            default -> GenerationOperationStatus.UNKNOWN;
        };
    }
}
