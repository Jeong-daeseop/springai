package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Figma 디자인 요청 오케스트레이션 (2-A5).
 * 분석 → 검증 → Spec 생성 → Bundle 조립 → Operation 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FigmaDesignOrchestrationService {

    private final FigmaContextAnalyzer contextAnalyzer;
    private final FigmaFileAllowlistValidator allowlistValidator;
    private final FigmaScreenExportService screenExportService;
    private final DesignArtifactService artifactService;
    private final FigmaDesignOperationRepository operationRepository;

    /**
     * 승인된 ScreenSpecification을 실제 Bundle과 불변 Artifact로 만든 뒤에만 PREVIEW_READY를 반환한다.
     */
    public FigmaDesignOperation processApprovedSpecificationRequest(
            FigmaDesignRequest request,
            FigmaScreenExportRequest exportRequest) {
        if (request == null || exportRequest == null) {
            throw new IllegalArgumentException("request와 exportRequest는 필수입니다");
        }
        allowlistValidator.validateFileKey(request.fileKey());

        var analysis = contextAnalyzer.analyze(request.prompt(), null);
        List<GenerationIssue> issues = confidenceIssues(analysis);
        FigmaDesignOperation initial = operationRepository.createOrReuse(request, exportRequest);
        if (initial.status() != FigmaDesignOperationStatus.ANALYZED) {
            return initial;
        }
        if (analysis.requiresReview()) {
            return operationRepository.appendTransition(
                    initial.operationId(), FigmaDesignOperationStatus.REJECTED,
                    null, issues, List.of());
        }

        var bundle = screenExportService.exportBundle(exportRequest);
        DesignArtifactService.FigmaBundleArtifact saved = artifactService.saveFigmaExportBundle(bundle);
        ArtifactRef artifact = new ArtifactRef(
                saved.artifactId(),
                "FIGMA_EXPORT_BUNDLE",
                saved.relativePath(),
                saved.contentHash(),
                saved.createdAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
        SourceRevisionRef sourceRevision = new SourceRevisionRef(
                exportRequest.screenSpecificationId(),
                bundle.metadata().screenSpecificationVersion() + ":" + saved.contentHash(),
                Instant.now());

        return operationRepository.appendTransition(
                initial.operationId(), FigmaDesignOperationStatus.PREVIEW_READY,
                sourceRevision, issues, List.of(artifact));
    }

    private List<GenerationIssue> confidenceIssues(FigmaContextAnalyzer.FigmaContextAnalysis analysis) {
        if (!analysis.requiresReview()) {
            return List.of();
        }
        return List.of(new GenerationIssue(
                "LOW_CONFIDENCE", GenerationIssue.Severity.WARNING, "CONTEXT_ANALYSIS", null,
                "LLM 분석 신뢰도 낮음: " + String.format("%.1f%%", analysis.uncertainty() * 100), null));
    }

    /**
     * 텍스트 요청 처리 (CREATE_FROM_TEXT).
     */
    public FigmaDesignOperation processTextRequest(String prompt, String fileKey) {
        return processExplicitRequest(FigmaDesignRequest.textDescription(prompt, fileKey));
    }

    /**
     * 명시적 요청 처리 (모든 타입).
     */
    public FigmaDesignOperation processExplicitRequest(FigmaDesignRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request는 필수입니다");
        }
        validateAdvancedRequest(request);
        allowlistValidator.validateFileKey(request.fileKey());
        var analysis = contextAnalyzer.analyze(request.prompt(), null);
        FigmaDesignOperation operation = operationRepository.createOrReuse(request);
        if (operation.status() != FigmaDesignOperationStatus.ANALYZED || !analysis.requiresReview()) {
            return operation;
        }
        return operationRepository.appendTransition(
                operation.operationId(), FigmaDesignOperationStatus.REJECTED,
                null, confidenceIssues(analysis), List.of());
    }

    private void validateAdvancedRequest(FigmaDesignRequest request) {
        switch (request.type()) {
            case REFERENCE_STYLE -> requireItems(request.referenceNodeIds(), "referenceNodeIds");
            case MODIFY_EXISTING -> requireItems(request.editableNodeIds(), "editableNodeIds");
            case IMAGE_REFERENCE -> requireItems(request.imageNodeIds(), "imageNodeIds");
            case MULTI_SCREEN_FLOW -> requireItems(request.screens(), "screens");
            case COMPONENT_SPECIFIED -> requireItems(request.components(), "components");
            case PLATFORM_CONVERT -> {
                requireItems(request.referenceNodeIds(), "sourceNodeIds");
                if (request.targetPlatform() == null
                        || !request.targetPlatform().matches("DESKTOP|TABLET|MOBILE")) {
                    throw new IllegalArgumentException("targetPlatform은 DESKTOP, TABLET, MOBILE 중 하나여야 합니다");
                }
            }
            case TEXT_DESCRIPTION -> { }
        }
    }

    private void requireItems(List<?> values, String name) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(name + "는 최소 1개 이상이어야 합니다");
        }
    }
}
