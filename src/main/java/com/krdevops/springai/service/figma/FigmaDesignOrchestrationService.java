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
import com.krdevops.springai.model.design.FigmaNodeIds;

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
    public FigmaDesignOperation processExplicitRequest(FigmaDesignRequest incoming) {
        if (incoming == null) {
            throw new IllegalArgumentException("request는 필수입니다");
        }
        validateAdvancedRequest(incoming);
        FigmaDesignRequest request = normalizeNodeIdFields(incoming);
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

    /**
     * Apply 시점 scope 재검증이 저장된 값을 그대로 조회하므로, 요청이 원장에 들어가기 전에
     * {@code 1-2}/{@code 1:2} 표기를 정규화하고 형식이 어긋나면 여기서 거부한다. 잘못된 nodeId를
     * 통과시키면 Apply에서 오탐 CONFLICT가 나는데, CONFLICT는 종단 상태이고 동일 requestHash는
     * {@code createOrReuse}가 재사용하므로 해당 요청이 영구히 복구 불가가 된다.
     *
     * <p>R6-041: {@code editableNodeIds}만 정규화되고 {@code referenceNodeIds}/{@code
     * imageNodeIds}는 형식 검증 없이 그대로 저장되던 격차를 닫는다 — 세 필드 모두 같은
     * {@link FigmaNodeIds} 단일 규칙을 통과해야 한다.
     */
    private FigmaDesignRequest normalizeNodeIdFields(FigmaDesignRequest request) {
        List<String> referenceNodeIds = FigmaNodeIds.normalizeAll(request.referenceNodeIds(), "referenceNodeIds");
        List<String> editableNodeIds = FigmaNodeIds.normalizeAll(request.editableNodeIds(), "editableNodeIds");
        List<String> imageNodeIds = FigmaNodeIds.normalizeAll(request.imageNodeIds(), "imageNodeIds");
        if (java.util.Objects.equals(referenceNodeIds, request.referenceNodeIds())
                && java.util.Objects.equals(editableNodeIds, request.editableNodeIds())
                && java.util.Objects.equals(imageNodeIds, request.imageNodeIds())) {
            return request;
        }
        return new FigmaDesignRequest(
                request.type(), request.prompt(), request.fileKey(), referenceNodeIds,
                editableNodeIds, imageNodeIds, request.targetPlatform(),
                request.components(), request.screens());
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
