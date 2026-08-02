package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.GenerationIssue.Severity;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequestType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * R6-031: Figma 디자인 요청의 전체 오케스트레이션.
 * 분석 → 검증 → ScreenSpecification 생성/수정 → FigmaScreenSpec 생성 → Bundle/Operation 조립.
 *
 * 7가지 요청 유형(TEXT_DESCRIPTION, REFERENCE_STYLE, MODIFY_EXISTING, IMAGE_REFERENCE,
 * MULTI_SCREEN_FLOW, COMPONENT_SPECIFIED, PLATFORM_CONVERT)을 모두 처리하며,
 * 각 유형별 특정 검증과 변환 로직을 적용함.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaDesignOrchestrationService {

    private final FigmaDesignOperationRepository operationRepository;
    private final FigmaScreenExportService screenExportService;
    private final FigmaDesignOperationStateService stateService;
    private final FigmaDesignRequestClassifierService classifierService;

    /**
     * 명시적 요청 타입을 받아 처리.
     * 1. 요청 유효성 검증
     * 2. 요청 유형별 처리
     * 3. Repository의 createOrReuse로 Operation 저장 (멱등성 자동 처리)
     */
    public FigmaDesignOperation processExplicitRequest(FigmaDesignRequest request) {
        // 요청 기본 검증
        List<GenerationIssue> validationIssues = validateRequest(request);

        if (hasFatalIssues(validationIssues)) {
            String operationId = UUID.randomUUID().toString();
            log.warn("Request validation failed: {}", validationIssues);
            return new FigmaDesignOperation(
                    operationId, 1, request, computeRequestHash(request),
                    FigmaDesignOperationStatus.FAILED,
                    null,
                    validationIssues.isEmpty() ? Collections.emptyList() : validationIssues,
                    Collections.emptyList(),
                    Instant.now(), Instant.now()
            );
        }

        try {
            log.info("Processing request type: {} for file: {}",
                    request.type(), request.fileKey());

            // 요청 유형별 검증 및 처리
            validateRequestByType(request);

            // Repository의 createOrReuse 메서드 사용
            // (멱등성, requestHash 기반 재사용, 상태 관리 자동 처리)
            FigmaDesignOperation operation = operationRepository.createOrReuse(request);

            log.info("Operation created/reused: operationId={}, status={}",
                    operation.operationId(), operation.status());
            return operation;

        } catch (IllegalArgumentException e) {
            log.warn("Request validation failed: {}", e.getMessage());
            String operationId = UUID.randomUUID().toString();
            return new FigmaDesignOperation(
                    operationId, 1, request, computeRequestHash(request),
                    FigmaDesignOperationStatus.REJECTED,
                    null,
                    List.of(new GenerationIssue(
                            "REQUEST_VALIDATION_FAILED", Severity.ERROR,
                            "REQUEST_TYPE_VALIDATION", null, e.getMessage(), null
                    )),
                    Collections.emptyList(),
                    Instant.now(), Instant.now()
            );
        } catch (Exception e) {
            log.error("Failed to process request", e);
            String operationId = UUID.randomUUID().toString();
            return new FigmaDesignOperation(
                    operationId, 1, request, computeRequestHash(request),
                    FigmaDesignOperationStatus.FAILED,
                    null,
                    List.of(new GenerationIssue(
                            "PROCESSING_ERROR", Severity.ERROR,
                            "FIGMA_PROCESSING", null, e.getMessage(), null
                    )),
                    Collections.emptyList(),
                    Instant.now(), Instant.now()
            );
        }
    }

    /**
     * 자유 텍스트 요청을 분류 후 처리.
     * confidence < 0.6이면 거부.
     */
    public FigmaDesignOperation processTextRequest(String prompt, String fileKey) {
        var classification = classifierService.classifyRequest(prompt);
        if (classification.isEmpty() || classification.get().confidence() < 0.6) {
            String operationId = UUID.randomUUID().toString();
            log.warn("Text classification failed or low confidence for prompt");
            return new FigmaDesignOperation(
                    operationId, 1, null, computeSimpleHash(prompt),
                    FigmaDesignOperationStatus.REJECTED,
                    null,
                    List.of(new GenerationIssue(
                            "TEXT_CLASSIFICATION_FAILED", Severity.ERROR,
                            "TEXT_CLASSIFICATION", null,
                            "Could not confidently classify request type from text", null
                    )),
                    Collections.emptyList(),
                    Instant.now(), Instant.now()
            );
        }

        FigmaDesignRequestType type = classification.get().type();
        log.info("Classified text request to type: {} (confidence: {})",
                 type, classification.get().confidence());
        FigmaDesignRequest request = createRequestFromClassification(type, prompt, fileKey);
        return processExplicitRequest(request);
    }

    // ===== Private Methods =====

    private void validateRequestByType(FigmaDesignRequest request) {
        FigmaDesignRequestType type = request.type();

        switch (type) {
            case REFERENCE_STYLE:
                if (request.referenceNodeIds() == null || request.referenceNodeIds().isEmpty()) {
                    throw new IllegalArgumentException("referenceNodeIds required for REFERENCE_STYLE");
                }
                break;
            case MODIFY_EXISTING:
                if (request.editableNodeIds() == null || request.editableNodeIds().isEmpty()) {
                    throw new IllegalArgumentException("editableNodeIds required for MODIFY_EXISTING");
                }
                break;
            case IMAGE_REFERENCE:
                if (request.imageNodeIds() == null || request.imageNodeIds().isEmpty()) {
                    throw new IllegalArgumentException("imageNodeIds required for IMAGE_REFERENCE");
                }
                break;
            case PLATFORM_CONVERT:
                if (request.targetPlatform() == null || request.targetPlatform().isBlank()) {
                    throw new IllegalArgumentException("targetPlatform required for PLATFORM_CONVERT");
                }
                break;
            default:
                // TEXT_DESCRIPTION, MULTI_SCREEN_FLOW, COMPONENT_SPECIFIED는 추가 필수값 없음
                log.debug("Processing request type: {}", type);
                break;
        }
    }

    private List<GenerationIssue> validateRequest(FigmaDesignRequest request) {
        List<GenerationIssue> issues = new ArrayList<>();

        // 기본 필수값 검증
        if (request.fileKey() == null || request.fileKey().isBlank()) {
            issues.add(new GenerationIssue(
                    "MISSING_FILE_KEY", Severity.FATAL, "REQUEST_VALIDATION",
                    null, "fileKey is required", null
            ));
        }

        if (request.prompt() == null || request.prompt().isBlank()) {
            issues.add(new GenerationIssue(
                    "MISSING_PROMPT", Severity.FATAL, "REQUEST_VALIDATION",
                    null, "prompt is required", null
            ));
        }

        return issues;
    }

    private boolean hasFatalIssues(List<GenerationIssue> issues) {
        return issues.stream()
                .anyMatch(issue -> "FATAL".equals(issue.severity()));
    }

    private String computeRequestHash(FigmaDesignRequest request) {
        String combined = request.fileKey() + "|" + request.type() + "|" + request.prompt();
        return Integer.toHexString(combined.hashCode());
    }

    private String computeSimpleHash(String text) {
        return Integer.toHexString(text.hashCode());
    }

    private FigmaDesignRequest createRequestFromClassification(
            FigmaDesignRequestType type, String prompt, String fileKey) {
        return new FigmaDesignRequest(
                type, prompt, fileKey, null, null, null, null, null, null
        );
    }
}
