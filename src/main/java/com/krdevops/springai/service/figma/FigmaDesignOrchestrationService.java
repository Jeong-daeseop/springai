package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.contract.GenerationIssue;
import com.krdevops.springai.model.contract.ArtifactRef;
import com.krdevops.springai.model.contract.SourceRevisionRef;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.SpecIssue;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperation;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.model.figma.contract.FigmaScreenRequest;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.DesignReferenceAnalysisService;
import com.krdevops.springai.service.ScreenSpecificationService;
import com.krdevops.springai.service.designsystem.ComponentRegistryResolver;
import com.krdevops.springai.service.designsystem.DesignSystemQueryService;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import com.krdevops.springai.service.FigmaApiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    /** {@link FigmaScreenExportService}의 기본 프로파일과 동일 — COMPONENT_SPECIFIED Registry 검증에 재사용. */
    private static final String DEFAULT_PROFILE_ID = "krds";

    private final FigmaContextAnalyzer contextAnalyzer;
    private final FigmaFileAllowlistValidator allowlistValidator;
    private final FigmaScreenExportService screenExportService;
    private final DesignArtifactService artifactService;
    private final FigmaDesignOperationRepository operationRepository;
    private final FigmaPlatformConversionService platformConversionService;
    private final DesignReferenceAnalysisService designReferenceAnalysisService;
    private final ScreenSpecificationService screenSpecificationService;
    private final ComponentRegistryResolver componentRegistryResolver;
    private final DesignSystemQueryService designSystemQueryService;
    private final FigmaApiClient figmaApiClient;

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

        var bundle = screenExportService.exportBundle(exportRequest).withOperationId(initial.operationId());
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
     * R6-032~038(2026-08-18): {@code processExplicitRequest}가 ANALYZED로 저장한 뒤, 실제
     * {@code ScreenSpecification}→{@code FigmaExportBundle} 생성까지 이어간다. 요청 유형별로
     * 재사용 가능한 조각(analyzeFigma/ComponentRegistryResolver/revise 등)을 연결하되, DB
     * 테이블에 바인딩되지 않는 요청은 {@code database}/{@code tableName}이 없다는 사실 자체를
     * FATAL Issue로 보고하고 REJECTED로 막는다(기존 CRUD 생성 아키텍처와 통일하는 방향으로 확정).
     *
     * <p>TEXT_DESCRIPTION은 이미 {@link #processApprovedSpecificationRequest}라는 별도 진입점이
     * 있으므로 이 메서드의 대상이 아니다. IMAGE_REFERENCE·PLATFORM_CONVERT는 각각 Figma 이미지
     * export 파이프라인과 노드 트리 변환이 추가로 필요해 별도 후속 작업으로 남긴다.
     */
    public FigmaDesignOperation generateBundle(String operationId) {
        FigmaDesignOperation operation = operationRepository.findLatest(operationId)
                .orElseThrow(() -> new IllegalArgumentException("Operation not found: " + operationId));
        if (operation.status() != FigmaDesignOperationStatus.ANALYZED) {
            throw new IllegalStateException(
                    "ANALYZED 상태의 Operation만 Bundle을 생성할 수 있습니다: " + operation.status());
        }
        FigmaDesignRequest request = operation.request();
        return switch (request.type()) {
            case REFERENCE_STYLE -> generateFromReference(operation, request);
            case IMAGE_REFERENCE -> generateFromImage(operation, request);
            case COMPONENT_SPECIFIED -> generateFromComponents(operation, request);
            case MODIFY_EXISTING -> generateFromModification(operation, request);
            case MULTI_SCREEN_FLOW -> generateMultiScreen(operation, request);
            case PLATFORM_CONVERT -> generateFromPlatformConversion(operation, request);
            case TEXT_DESCRIPTION -> throw new IllegalArgumentException(
                    "이 요청 유형(" + request.type() + ")은 아직 자동 Bundle 생성을 지원하지 않습니다.");
        };
    }

    /**
     * R6-038: {@code screenSpecificationId}가 가리키는 기존 화면을 {@code targetPlatform} 기준으로
     * export한 뒤, {@link FigmaPlatformConversionService}가 계산한 Component Swap 결정을
     * {@code FigmaNodeSpec} 트리에 실제로 반영해 새 Bundle로 저장한다. Grid 폭·열 수 재계산은
     * {@code FigmaScreenSpec}에 그런 필드 자체가 없어(BASE-18) 여전히 범위 밖이며, {@code viewport}
     * 문자열 갱신과 Component Swap만 이번에 실제로 연결한다(Plugin R5-044와 동일한 경계).
     */
    private FigmaDesignOperation generateFromPlatformConversion(
            FigmaDesignOperation operation, FigmaDesignRequest request) {
        if (isBlank(request.screenSpecificationId())) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_ID_REQUIRED",
                    "PLATFORM_CONVERT는 변환할 screenSpecificationId가 필요합니다.");
        }
        ScreenSpecification spec;
        try {
            spec = screenSpecificationService.get(request.screenSpecificationId());
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_NOT_FOUND", e.getMessage());
        }
        if (spec.status() != ScreenSpecStatus.APPROVED) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_NOT_APPROVED",
                    "APPROVED 상태의 화면명세만 플랫폼 변환할 수 있습니다: " + spec.id());
        }
        if (spec.pages().isEmpty()) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_NO_PAGES",
                    "변환할 화면명세에 페이지가 없습니다: " + spec.id());
        }
        FigmaExportBundle sourceBundle;
        try {
            FigmaScreenExportRequest exportRequest = new FigmaScreenExportRequest(
                    spec.id(), spec.version(), spec.pages().get(0).id(), null, "DESKTOP",
                    FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW);
            sourceBundle = screenExportService.exportBundle(exportRequest);
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "BUNDLE_EXPORT_FAILED", e.getMessage());
        }

        ComponentRegistry registry = sourceBundle.componentRegistry().registry();
        Set<String> logicalTypes = collectLogicalTypes(sourceBundle.figmaScreenSpec().content());
        FigmaPlatformConversionService.PlatformConversionResult conversion;
        try {
            conversion = platformConversionService.convert(request.targetPlatform(), List.copyOf(logicalTypes));
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "PLATFORM_CONVERSION_FAILED", e.getMessage());
        }
        Map<String, String> swapTargets = conversion.componentSwaps().stream()
                .filter(FigmaPlatformConversionService.ComponentSwapDecision::swapped)
                .collect(Collectors.toMap(
                        FigmaPlatformConversionService.ComponentSwapDecision::requestedLogicalType,
                        FigmaPlatformConversionService.ComponentSwapDecision::resolvedLogicalType));
        FigmaNodeSpec convertedContent = applyComponentSwaps(
                sourceBundle.figmaScreenSpec().content(), swapTargets, registry);

        FigmaScreenSpec sourceSpec = sourceBundle.figmaScreenSpec();
        FigmaScreenSpec convertedSpec = new FigmaScreenSpec(
                sourceSpec.screenId() + "-" + request.targetPlatform().toLowerCase(Locale.ROOT),
                sourceSpec.screenVersion(), sourceSpec.screenSpecificationId(),
                sourceSpec.screenSpecificationVersion(), sourceSpec.screenType(), sourceSpec.layoutPattern(),
                sourceSpec.name(), sourceSpec.route(), request.targetPlatform(), sourceSpec.status(),
                sourceSpec.designSystem(), convertedContent, sourceSpec.issues(),
                sourceSpec.semanticPattern(), sourceSpec.screenPatternVersion(),
                sourceSpec.variantRuleSetVersion(), sourceSpec.componentContractVersion());
        FigmaExportBundle convertedBundle = new FigmaExportBundle(
                convertedSpec, sourceBundle.designSystemProfile(), sourceBundle.componentRegistry(),
                sourceBundle.resolvedComponentRegistry(), sourceBundle.screenPattern(),
                sourceBundle.variantRuleSet(), sourceBundle.metadata())
                .withOperationId(operation.operationId());

        DesignArtifactService.FigmaBundleArtifact saved = artifactService.saveFigmaExportBundle(convertedBundle);
        ArtifactRef artifact = new ArtifactRef(
                saved.artifactId(), "FIGMA_EXPORT_BUNDLE", saved.relativePath(), saved.contentHash(),
                saved.createdAt().atZone(ZoneId.systemDefault()).toInstant());
        List<GenerationIssue> issues = swapTargets.isEmpty() ? List.of() : List.of(new GenerationIssue(
                "PLATFORM_CONVERT_GRID_NOT_RECALCULATED", GenerationIssue.Severity.WARNING, "PLATFORM_CONVERT",
                convertedSpec.screenId(),
                "Component Swap " + swapTargets.size() + "건은 반영했지만 Grid 폭·열 수 재계산은 지원하지 않습니다.",
                "Layout은 Plugin Apply 후 사람이 직접 확인하세요."));
        SourceRevisionRef sourceRevision = new SourceRevisionRef(
                spec.id(), convertedBundle.metadata().screenSpecificationVersion() + ":" + saved.contentHash(),
                Instant.now());
        return operationRepository.appendTransition(
                operation.operationId(), FigmaDesignOperationStatus.PREVIEW_READY,
                sourceRevision, issues, List.of(artifact));
    }

    /** 트리 전체에서 실제 쓰인 논리 컴포넌트 타입만 모아 Swap 대상 조회 범위를 좁힌다. */
    /* package-private: FigmaDesignOrchestrationServiceTest가 트리 재작성 로직을 직접 검증한다. */
    Set<String> collectLogicalTypes(FigmaNodeSpec node) {
        Set<String> types = new LinkedHashSet<>();
        collectLogicalTypes(node, types);
        return types;
    }

    private void collectLogicalTypes(FigmaNodeSpec node, Set<String> accumulator) {
        if (node.componentResolution() != null) {
            accumulator.add(node.componentResolution().logicalType());
        }
        node.children().forEach(child -> collectLogicalTypes(child, accumulator));
    }

    /**
     * Plugin {@code core.ts:applyComponentSwaps()}의 Java 판본. {@code swapped=true}로 결정된
     * 논리 타입만 바꾸며, 대체 대상이 Registry에 없으면 원본 컴포넌트를 그대로 유지한다(같은
     * "조용히 잘못된 componentSetKey를 만들지 않는다" 원칙).
     */
    /* package-private: FigmaDesignOrchestrationServiceTest가 트리 재작성 로직을 직접 검증한다. */
    FigmaNodeSpec applyComponentSwaps(
            FigmaNodeSpec node, Map<String, String> swapTargets, ComponentRegistry registry) {
        List<FigmaNodeSpec> children = node.children().stream()
                .map(child -> applyComponentSwaps(child, swapTargets, registry))
                .toList();
        ResolvedComponentRef resolution = node.componentResolution();
        String targetLogicalType = resolution == null ? null : swapTargets.get(resolution.logicalType());
        if (resolution == null || targetLogicalType == null) {
            return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), node.type(), node.properties(),
                    resolution, children);
        }
        ComponentRegistryEntry targetEntry = registry.components().get(targetLogicalType);
        if (targetEntry == null) {
            return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), node.type(), node.properties(),
                    resolution, children);
        }
        ResolvedComponentRef swapped = new ResolvedComponentRef(
                resolution.role(), targetLogicalType, targetEntry.componentSetKey(),
                "", resolution.variantProperties(), resolution.componentProperties(),
                resolution.contractVersion(), resolution.ruleSetVersion(), resolution.ruleId(),
                resolution.contextHash());
        return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), node.type(), node.properties(),
                swapped, children);
    }

    /**
     * R6-035: Figma 이미지 노드를 실제 PNG로 export해 Vision 분석 → DB 테이블에 바인딩된 새 화면을
     * 만든다. {@code queryImages()}가 준 CDN URL을 {@code app.design-vision.allowed-paths}에
     * 이미 포함된 {@code java.io.tmpdir}로 내려받아 기존 {@code analyzeDesignReference} 경로를
     * 그대로 재사용한다(별도 허용 경로 설정 불필요).
     */
    private FigmaDesignOperation generateFromImage(FigmaDesignOperation operation, FigmaDesignRequest request) {
        if (isBlank(request.database()) || isBlank(request.tableName())) {
            return rejectWithMessage(operation, "DATABASE_TABLE_REQUIRED",
                    "실제 화면 Bundle 생성에는 database/tableName이 필요합니다(기존 CRUD 생성 아키텍처와 통일).");
        }
        String nodeId = request.imageNodeIds().get(0);
        FigmaApiClient.FigmaImageUrls images;
        try {
            images = figmaApiClient.queryImages(request.fileKey(), List.of(nodeId));
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "FIGMA_IMAGE_EXPORT_FAILED", e.getMessage());
        }
        String imageUrl = images.imageUrlsByNodeId().get(nodeId);
        if (imageUrl == null) {
            return rejectWithMessage(operation, "FIGMA_IMAGE_EXPORT_FAILED",
                    "이미지 렌더링에 실패한 노드입니다: " + nodeId);
        }
        Path tempFile;
        try {
            tempFile = downloadToTempFile(imageUrl);
        } catch (IOException | RuntimeException e) {
            return rejectWithMessage(operation, "FIGMA_IMAGE_DOWNLOAD_FAILED", e.getMessage());
        }
        try {
            DesignAnalysisResult analysis;
            try {
                analysis = designReferenceAnalysisService.analyze(
                        tempFile.toString(), null, request.featureType());
            } catch (RuntimeException e) {
                return rejectWithMessage(operation, "IMAGE_ANALYSIS_FAILED", e.getMessage());
            }
            return generateFromScreenSpecification(operation, request, analysis.uiSpec());
        } finally {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("임시 이미지 파일 삭제 실패: {}", tempFile, e);
            }
        }
    }

    private Path downloadToTempFile(String imageUrl) throws IOException {
        URI uri = URI.create(imageUrl);
        String lower = imageUrl.toLowerCase(Locale.ROOT);
        String extension = lower.contains(".jpg") || lower.contains(".jpeg") ? "jpg" : "png";
        Path tempFile = Files.createTempFile("figma-image-", "." + extension);
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(30_000);
        try (InputStream input = connection.getInputStream()) {
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    /** R6-033: Figma 참조 화면을 분석해 DB 테이블에 바인딩된 새 화면을 만든다. */
    private FigmaDesignOperation generateFromReference(FigmaDesignOperation operation, FigmaDesignRequest request) {
        if (isBlank(request.database()) || isBlank(request.tableName())) {
            return rejectWithMessage(operation, "DATABASE_TABLE_REQUIRED",
                    "실제 화면 Bundle 생성에는 database/tableName이 필요합니다(기존 CRUD 생성 아키텍처와 통일).");
        }
        String nodeId = request.referenceNodeIds().get(0);
        String syntheticFigmaUrl = "https://www.figma.com/design/" + request.fileKey() + "/reference";
        DesignAnalysisResult analysis;
        try {
            analysis = designReferenceAnalysisService.analyzeFigma(syntheticFigmaUrl, nodeId, request.featureType());
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "FIGMA_REFERENCE_ANALYSIS_FAILED", e.getMessage());
        }
        return generateFromScreenSpecification(operation, request, analysis.uiSpec());
    }

    /** R6-037: 요청 컴포넌트가 전부 Registry에서 해석돼야만 DB 테이블 기반 화면 생성으로 이어간다. */
    private FigmaDesignOperation generateFromComponents(FigmaDesignOperation operation, FigmaDesignRequest request) {
        ComponentRegistry registry;
        try {
            registry = designSystemQueryService.findLatestRegistry(DEFAULT_PROFILE_ID);
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "COMPONENT_REGISTRY_NOT_FOUND", e.getMessage());
        }
        List<String> unresolved = request.components().stream()
                .filter(logicalType -> !componentRegistryResolver.resolve(registry, logicalType).resolved())
                .toList();
        if (!unresolved.isEmpty()) {
            List<GenerationIssue> issues = unresolved.stream()
                    .map(logicalType -> new GenerationIssue(
                            "COMPONENT_NOT_IN_REGISTRY", GenerationIssue.Severity.ERROR, "COMPONENT_VALIDATION",
                            logicalType, "Registry에서 확인할 수 없는 컴포넌트입니다: " + logicalType,
                            "논리 컴포넌트 타입을 Registry에 등록하거나 다른 타입을 요청하세요."))
                    .toList();
            return operationRepository.appendTransition(
                    operation.operationId(), FigmaDesignOperationStatus.REJECTED, null, issues, List.of());
        }
        return generateFromScreenSpecification(operation, request, null);
    }

    /**
     * R6-034: {@code screenSpecificationId}로 지정한 기존 화면을 현재 DB 스키마 기준으로
     * 재검증·재생성한다. 자유 텍스트 prompt를 필드 단위 변경으로 해석하는 LLM diff 엔진은 없다 —
     * "무엇을 바꿀지"는 항상 DB 스키마가 결정하며, 이 호출은 스키마 변경분을 재동기화하는 성격이다.
     */
    private FigmaDesignOperation generateFromModification(FigmaDesignOperation operation, FigmaDesignRequest request) {
        if (isBlank(request.screenSpecificationId())) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_ID_REQUIRED",
                    "MODIFY_EXISTING은 재생성할 screenSpecificationId가 필요합니다.");
        }
        ScreenSpecification current;
        try {
            current = screenSpecificationService.get(request.screenSpecificationId());
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_NOT_FOUND", e.getMessage());
        }
        ScreenSpecification revised;
        try {
            revised = screenSpecificationService.revise(current);
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_REVISE_FAILED", e.getMessage());
        }
        return finalizeFromScreenSpecification(operation, revised);
    }

    /**
     * R6-036: 화면마다 독립적으로 생성·검증하되, 하나라도 실패하면 이미 만들어진 Bundle이 있어도
     * Operation 자체는 PREVIEW_READY로 전이하지 않는다(all-or-nothing). 개별 화면의 Artifact
     * 파일은 실패 후에도 저장소에 남을 수 있으나(멱등 저장·Archive 지향 정책과 동일하게 삭제하지
     * 않음), Operation이 참조하지 않으므로 Plugin Apply 대상이 되지 않는다.
     */
    private FigmaDesignOperation generateMultiScreen(FigmaDesignOperation operation, FigmaDesignRequest request) {
        List<ArtifactRef> artifacts = new ArrayList<>();
        List<GenerationIssue> issues = new ArrayList<>();
        for (FigmaScreenRequest screen : request.screens()) {
            if (isBlank(screen.database()) || isBlank(screen.tableName())) {
                issues.add(new GenerationIssue("DATABASE_TABLE_REQUIRED", GenerationIssue.Severity.FATAL,
                        "MULTI_SCREEN_FLOW", screen.name(),
                        "화면 '" + screen.name() + "'에 database/tableName이 필요합니다.", null));
                continue;
            }
            ScreenSpecification spec;
            try {
                spec = screenSpecificationService.create(
                        screen.database(), screen.tableName(), screen.name(), request.featureType(), null);
            } catch (RuntimeException e) {
                issues.add(new GenerationIssue("SCREEN_SPECIFICATION_CREATION_FAILED",
                        GenerationIssue.Severity.FATAL, "MULTI_SCREEN_FLOW", screen.name(), e.getMessage(), null));
                continue;
            }
            if (spec.status() != ScreenSpecStatus.APPROVED) {
                issues.add(new GenerationIssue("SCREEN_SPECIFICATION_REVIEW_REQUIRED",
                        GenerationIssue.Severity.FATAL, "MULTI_SCREEN_FLOW", spec.id(),
                        "화면 '" + screen.name() + "'의 명세가 REVIEW_REQUIRED입니다(screenSpecificationId="
                                + spec.id() + ").", null));
                continue;
            }
            if (spec.pages().isEmpty()) {
                issues.add(new GenerationIssue("SCREEN_SPECIFICATION_NO_PAGES",
                        GenerationIssue.Severity.FATAL, "MULTI_SCREEN_FLOW", spec.id(),
                        "생성된 화면명세에 페이지가 없습니다: " + spec.id(), null));
                continue;
            }
            try {
                FigmaScreenExportRequest exportRequest = new FigmaScreenExportRequest(
                        spec.id(), spec.version(), spec.pages().get(0).id(), null, "DESKTOP",
                        FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW);
                var bundle = screenExportService.exportBundle(exportRequest).withOperationId(operation.operationId());
                DesignArtifactService.FigmaBundleArtifact saved = artifactService.saveFigmaExportBundle(bundle);
                artifacts.add(new ArtifactRef(saved.artifactId(), "FIGMA_EXPORT_BUNDLE", saved.relativePath(),
                        saved.contentHash(), saved.createdAt().atZone(ZoneId.systemDefault()).toInstant()));
            } catch (RuntimeException e) {
                issues.add(new GenerationIssue("BUNDLE_EXPORT_FAILED", GenerationIssue.Severity.FATAL,
                        "MULTI_SCREEN_FLOW", spec.id(), e.getMessage(), null));
            }
        }
        boolean anyFatal = issues.stream().anyMatch(issue -> issue.severity() == GenerationIssue.Severity.FATAL);
        if (anyFatal) {
            return operationRepository.appendTransition(
                    operation.operationId(), FigmaDesignOperationStatus.REJECTED, null, issues, List.of());
        }
        return operationRepository.appendTransition(
                operation.operationId(), FigmaDesignOperationStatus.PREVIEW_READY, null, issues, artifacts);
    }

    /** REFERENCE_STYLE/COMPONENT_SPECIFIED가 공유하는 "DB 테이블 바인딩 → ScreenSpecification 생성" 경로. */
    private FigmaDesignOperation generateFromScreenSpecification(
            FigmaDesignOperation operation, FigmaDesignRequest request, UiDesignSpec uiSpec) {
        if (isBlank(request.database()) || isBlank(request.tableName())) {
            return rejectWithMessage(operation, "DATABASE_TABLE_REQUIRED",
                    "실제 화면 Bundle 생성에는 database/tableName이 필요합니다(기존 CRUD 생성 아키텍처와 통일).");
        }
        ScreenSpecification spec;
        try {
            spec = screenSpecificationService.create(
                    request.database(), request.tableName(), request.screenName(), request.featureType(), uiSpec);
        } catch (RuntimeException e) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_CREATION_FAILED", e.getMessage());
        }
        return finalizeFromScreenSpecification(operation, spec);
    }

    /** ScreenSpecification이 자동으로 APPROVED됐으면 곧바로 Bundle을 만들고, 아니면 사람 검토로 넘긴다. */
    private FigmaDesignOperation finalizeFromScreenSpecification(
            FigmaDesignOperation operation, ScreenSpecification spec) {
        if (spec.status() != ScreenSpecStatus.APPROVED) {
            return rejectReviewRequired(operation, spec);
        }
        if (spec.pages().isEmpty()) {
            return rejectWithMessage(operation, "SCREEN_SPECIFICATION_NO_PAGES",
                    "생성된 화면명세에 페이지가 없습니다: " + spec.id());
        }
        return exportAndTransition(operation, spec);
    }

    private FigmaDesignOperation exportAndTransition(FigmaDesignOperation operation, ScreenSpecification spec) {
        FigmaScreenExportRequest exportRequest = new FigmaScreenExportRequest(
                spec.id(), spec.version(), spec.pages().get(0).id(), null, "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW);
        var bundle = screenExportService.exportBundle(exportRequest).withOperationId(operation.operationId());
        DesignArtifactService.FigmaBundleArtifact saved = artifactService.saveFigmaExportBundle(bundle);
        ArtifactRef artifact = new ArtifactRef(
                saved.artifactId(), "FIGMA_EXPORT_BUNDLE", saved.relativePath(), saved.contentHash(),
                saved.createdAt().atZone(ZoneId.systemDefault()).toInstant());
        SourceRevisionRef sourceRevision = new SourceRevisionRef(
                spec.id(), bundle.metadata().screenSpecificationVersion() + ":" + saved.contentHash(),
                Instant.now());
        return operationRepository.appendTransition(
                operation.operationId(), FigmaDesignOperationStatus.PREVIEW_READY,
                sourceRevision, List.of(), List.of(artifact));
    }

    /**
     * REVIEW_REQUIRED로 남은 ScreenSpecification은 자동으로 밀어붙이지 않는다 — 사람이
     * {@code reviseScreenSpecification}/{@code approveScreenSpecification}으로 직접 고친 뒤,
     * 이미 있는 {@code createFigmaBundleFromApprovedSpecification}으로 Bundle을 만들어야 한다.
     */
    private FigmaDesignOperation rejectReviewRequired(FigmaDesignOperation operation, ScreenSpecification spec) {
        List<GenerationIssue> issues = new ArrayList<>();
        issues.add(new GenerationIssue(
                "SCREEN_SPECIFICATION_REVIEW_REQUIRED", GenerationIssue.Severity.WARNING, "SCREEN_SPECIFICATION",
                spec.id(),
                "생성된 화면명세가 REVIEW_REQUIRED 상태입니다(screenSpecificationId=" + spec.id() + ").",
                "reviseScreenSpecification으로 수정하고 approveScreenSpecification으로 승인한 뒤 "
                        + "createFigmaBundleFromApprovedSpecification을 직접 호출하세요."));
        for (SpecIssue issue : spec.issues()) {
            issues.add(new GenerationIssue(
                    issue.code(), mapSeverity(issue.severity()), "SCREEN_SPECIFICATION", spec.id(),
                    issue.message(), null));
        }
        return operationRepository.appendTransition(
                operation.operationId(), FigmaDesignOperationStatus.REJECTED, null, issues, List.of());
    }

    private FigmaDesignOperation rejectWithMessage(FigmaDesignOperation operation, String code, String message) {
        List<GenerationIssue> issues = List.of(new GenerationIssue(
                code, GenerationIssue.Severity.FATAL, "SCREEN_SPECIFICATION", null, message, null));
        return operationRepository.appendTransition(
                operation.operationId(), FigmaDesignOperationStatus.REJECTED, null, issues, List.of());
    }

    private GenerationIssue.Severity mapSeverity(SpecIssue.Severity severity) {
        return switch (severity) {
            case ERROR -> GenerationIssue.Severity.ERROR;
            case WARNING, INFO -> GenerationIssue.Severity.WARNING;
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
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
        return request.withNodeIds(referenceNodeIds, editableNodeIds, imageNodeIds);
    }

    private void validateAdvancedRequest(FigmaDesignRequest request) {
        switch (request.type()) {
            case REFERENCE_STYLE -> requireItems(request.referenceNodeIds(), "referenceNodeIds");
            case MODIFY_EXISTING -> requireItems(request.editableNodeIds(), "editableNodeIds");
            case IMAGE_REFERENCE -> requireItems(request.imageNodeIds(), "imageNodeIds");
            case MULTI_SCREEN_FLOW -> requireItems(request.screens(), "screens");
            case COMPONENT_SPECIFIED -> {
                requireItems(request.components(), "components");
                // R6-T12: components는 논리 컴포넌트 타입이어야 한다 — 원시 Figma nodeId를
                // 직접 넣으면 Registry의 논리 타입 allowlist를 우회하므로 여기서 차단한다.
                List<String> nodeIdShaped = request.components().stream()
                        .filter(FigmaNodeIds::isNodeIdShaped)
                        .toList();
                if (!nodeIdShaped.isEmpty()) {
                    throw new IllegalArgumentException(
                            "components는 논리 컴포넌트 타입(예: krds.button)이어야 하며 "
                                    + "Figma nodeId를 직접 지정할 수 없습니다: " + nodeIdShaped);
                }
            }
            case PLATFORM_CONVERT -> {
                requireItems(request.referenceNodeIds(), "sourceNodeIds");
                // R6-046: 하드코딩 정규식 대신 FigmaPlatformConversionService가 소유한 Viewport
                // 정책을 단일 기준으로 삼는다 — 지원 플랫폼 목록이 갈라지는 것을 막는다.
                if (!platformConversionService.isSupportedPlatform(request.targetPlatform())) {
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
