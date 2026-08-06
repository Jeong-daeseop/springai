package com.krdevops.springai.service;

import com.krdevops.springai.config.DesignVisionProperties;
import com.krdevops.springai.mapper.DesignAnalysisRepository;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.model.design.FigmaNodeDocument;
import com.krdevops.springai.model.design.FigmaReference;
import com.krdevops.springai.model.design.FigmaSource;
import com.krdevops.springai.model.design.FileDesignSourceMetadata;
import com.krdevops.springai.model.design.FigmaDesignSourceMetadata;
import com.krdevops.springai.model.design.SemanticDesignCandidate;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.VisionAnalysisRequest;
import com.krdevops.springai.service.resilience.ExternalCallGuard;
import com.krdevops.springai.service.resilience.ExternalDependency;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class DesignReferenceAnalysisService {

    private static final Pattern ANALYSIS_ID_PATTERN = Pattern.compile("분석ID:\\s*([A-Za-z0-9-]+)");

    private final ReferencePathValidator pathValidator;
    private final PdfPageRasterizer pdfPageRasterizer;
    private final ImagePreprocessor imagePreprocessor;
    private final VisionAnalysisClient visionAnalysisClient;
    private final DesignAnalysisRepository repository;
    private final RagService ragService;
    private final DesignVisionProperties properties;
    private final FigmaReferenceValidator figmaReferenceValidator;
    private final FigmaApiClient figmaApiClient;
    private final FigmaDesignSpecMapper figmaDesignSpecMapper;
    private final FigmaCacheKeyFactory figmaCacheKeyFactory;
    private Executor visionExecutor = ForkJoinPool.commonPool();
    private ExternalCallGuard externalCallGuard;

    @Autowired
    void configureOperationalIsolation(@Qualifier("visionExecutor") Executor visionExecutor,
                                       ExternalCallGuard externalCallGuard) {
        this.visionExecutor = visionExecutor;
        this.externalCallGuard = externalCallGuard;
    }

    public DesignAnalysisResult analyze(String referencePath, String pageRange, String featureType) {
        Path path = pathValidator.validate(referencePath);
        String normalizedFeatureType = normalizeFeatureType(featureType);
        String sourceHash = sourceHash(path, pageRange, normalizedFeatureType);
        return repository.findExact(sourceHash, visionAnalysisClient.providerId(),
                        visionAnalysisClient.modelId(), properties.getPromptVersion())
                .orElseGet(() -> analyzeAndSave(path, pageRange, normalizedFeatureType, sourceHash));
    }

    public DesignAnalysisResult analyzeFigma(
            String figmaUrl, String explicitNodeId, String featureType) {
        String normalizedFeatureType = normalizeFeatureType(featureType);
        FigmaReference reference = figmaReferenceValidator.validate(figmaUrl, explicitNodeId);
        FigmaNodeDocument document = figmaApiClient.fetchNode(reference);
        String mapperVersion = properties.getFigma().getMapperVersion();
        String sourceHash = figmaCacheKeyFactory.create(
                reference, document.fileVersion(), normalizedFeatureType);
        return repository.findExact(sourceHash, "figma", "deterministic-mapper", mapperVersion)
                .orElseGet(() -> analyzeFigmaAndSave(reference, document, normalizedFeatureType,
                        sourceHash, mapperVersion));
    }

    public DesignAnalysisResult get(String analysisId) {
        return repository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("디자인 분석 결과를 찾을 수 없습니다: " + analysisId));
    }

    public List<String> findSemanticCandidates(String query, int topK) {
        return ragService.search(query, topK).stream()
                .filter(document -> "design_analysis".equals(document.getMetadata().get("type")))
                .map(Document::getText)
                .toList();
    }

    /**
     * 유사도 검색 결과를 DB 원본과 현재 provider/model/prompt 계약으로 다시 검증한다.
     * RAG 결과만으로 자동 재사용하지 않으며 reusable=true인 결과만 사람이 선택할 수 있다.
     */
    public List<SemanticDesignCandidate> findReusableCandidates(
            String query, String expectedArchetype, int topK) {
        return findReusableCandidates(query, expectedArchetype,
                featureTypeFromArchetype(expectedArchetype), topK);
    }

    public List<SemanticDesignCandidate> findReusableCandidates(
            String query, String expectedArchetype, String expectedFeatureType, int topK) {
        List<Document> documents = ragService.search(query, Math.max(1, topK));
        Set<String> ids = new LinkedHashSet<>();
        for (Document document : documents) {
            if (!"design_analysis".equals(document.getMetadata().get("type"))) continue;
            Matcher matcher = ANALYSIS_ID_PATTERN.matcher(document.getText());
            if (matcher.find()) ids.add(matcher.group(1));
        }

        List<SemanticDesignCandidate> result = new ArrayList<>();
        int rank = 1;
        for (String id : ids) {
            DesignAnalysisResult analysis = repository.findById(id).orElse(null);
            if (analysis == null) continue;
            List<String> reasons = new ArrayList<>();
            if (expectedArchetype != null && !expectedArchetype.isBlank()
                    && !expectedArchetype.equalsIgnoreCase(analysis.uiSpec().archetype())) {
                reasons.add("archetype 불일치");
            }
            String normalizedExpectedFeatureType = expectedFeatureType == null
                    || expectedFeatureType.isBlank() ? null : normalizeFeatureType(expectedFeatureType);
            if (normalizedExpectedFeatureType != null
                    && !normalizedExpectedFeatureType.equalsIgnoreCase(analysis.featureType())) {
                reasons.add("featureType 불일치");
            }
            if (!UiDesignSpec.SCHEMA_VERSION.equalsIgnoreCase(analysis.uiSpecSchemaVersion())) {
                reasons.add("UiDesignSpec schema version 불일치");
            }
            checkExecutionContract(analysis, reasons);
            result.add(new SemanticDesignCandidate(
                    id, rank++, analysis.uiSpec().archetype(), analysis.provider(), analysis.model(),
                    analysis.promptVersion(), reasons.isEmpty(), reasons));
        }
        return List.copyOf(result);
    }

    private DesignAnalysisResult analyzeAndSave(
            Path path, String pageRange, String featureType, String sourceHash) {
        List<VisionAnalysisRequest.VisionImage> sourceImages = loadImages(path, pageRange);
        List<VisionAnalysisRequest.VisionImage> images = imagePreprocessor.preprocess(sourceImages);
        UiDesignSpec uiSpec = analyzeWithTimeout(
                new VisionAnalysisRequest(featureType, images, properties.getPromptVersion()));
        DesignAnalysisResult result = new DesignAnalysisResult(
                UUID.randomUUID().toString(), sourceHash, path.toString(), pageRange,
                DesignSourceType.FILE, null, properties.getPromptVersion(),
                UiDesignSpec.SCHEMA_VERSION, featureType,
                visionAnalysisClient.providerId(), visionAnalysisClient.modelId(), properties.getPromptVersion(),
                sourceImages.stream().map(VisionAnalysisRequest.VisionImage::pageNumber).toList(),
                uiSpec, uiSpec.uncertainties(), LocalDateTime.now(),
                new FileDesignSourceMetadata(path.toString(), pageRange));
        DesignAnalysisSaveOutcome outcome = repository.saveOrGet(result);
        if (outcome.insertedByCaller()) ingestBestEffort(outcome.result());
        return outcome.result();
    }

    private DesignAnalysisResult analyzeFigmaAndSave(
            FigmaReference reference, FigmaNodeDocument document, String featureType,
            String sourceHash, String mapperVersion) {
        UiDesignSpec uiSpec = figmaDesignSpecMapper.map(document, featureType);
        DesignAnalysisResult result = new DesignAnalysisResult(
                UUID.randomUUID().toString(), sourceHash,
                "figma://" + reference.fileKey() + "#" + reference.nodeId(), null,
                DesignSourceType.FIGMA,
                new FigmaSource(reference.fileKey(), reference.nodeId(), document.fileVersion()),
                mapperVersion, UiDesignSpec.SCHEMA_VERSION, featureType,
                "figma", "deterministic-mapper", mapperVersion,
                List.of(), uiSpec, uiSpec.uncertainties(), LocalDateTime.now(),
                new FigmaDesignSourceMetadata(reference.fileKey(), reference.nodeId(), document.fileVersion()));
        DesignAnalysisSaveOutcome outcome = repository.saveOrGet(result);
        if (outcome.insertedByCaller()) ingestBestEffort(outcome.result());
        return outcome.result();
    }

    private List<VisionAnalysisRequest.VisionImage> loadImages(Path path, String pageRange) {
        try {
            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".pdf")) return pdfPageRasterizer.rasterize(path, pageRange);
            String mimeType = name.endsWith(".png") ? "image/png" : "image/jpeg";
            return List.of(new VisionAnalysisRequest.VisionImage(1, mimeType, Files.readAllBytes(path)));
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IllegalStateException("디자인 참조 이미지를 읽을 수 없습니다: " + path, e);
        }
    }

    private String sourceHash(Path path, String pageRange, String featureType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String fileDigest = HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));
            String canonical = "sourceType=FILE\n"
                    + "fileSha256=" + fileDigest + "\n"
                    + "pageRange=" + (pageRange == null ? "" : pageRange.trim()) + "\n"
                    + "featureType=" + featureType;
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("디자인 참조 해시 생성 실패", e);
        }
    }

    private void checkExecutionContract(DesignAnalysisResult analysis, List<String> reasons) {
        DesignSourceType sourceType = analysis.sourceMetadata().sourceType();
        if (sourceType == DesignSourceType.WEB_CAPTURE) {
            reasons.add("WEB_CAPTURE는 Release 1 시맨틱 재사용 대상이 아님");
            return;
        }
        if (sourceType == DesignSourceType.FIGMA) {
            if (!properties.getFigma().isEnabled()) reasons.add("Figma 분석 비활성");
            if (!"figma".equalsIgnoreCase(analysis.provider())) reasons.add("provider 불일치");
            if (!"deterministic-mapper".equalsIgnoreCase(analysis.model())) reasons.add("model 불일치");
            if (!properties.getFigma().getMapperVersion()
                    .equalsIgnoreCase(analysis.analysisContractVersion())) {
                reasons.add("analysisContractVersion 불일치");
            }
            return;
        }
        if (!visionAnalysisClient.providerId().equalsIgnoreCase(analysis.provider())) {
            reasons.add("provider 불일치");
        }
        if (!visionAnalysisClient.modelId().equalsIgnoreCase(analysis.model())) {
            reasons.add("model 불일치");
        }
        if (!properties.getPromptVersion().equalsIgnoreCase(analysis.promptVersion())) {
            reasons.add("promptVersion 불일치");
        }
    }

    private String normalizeFeatureType(String featureType) {
        return featureType == null || featureType.isBlank()
                ? "crud" : featureType.trim().toLowerCase(Locale.ROOT);
    }

    private String featureTypeFromArchetype(String archetype) {
        if (archetype == null || archetype.isBlank()) return null;
        String normalized = archetype.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("BOARD")) return "board";
        if (normalized.startsWith("CRUD") || normalized.startsWith("MASTER_DETAIL")) return "crud";
        return null;
    }

    private void ingestBestEffort(DesignAnalysisResult result) {
        UiDesignSpec spec = result.uiSpec();
        String content = "분석ID: %s | archetype: %s | layout: %s | components: %s | fields: %s | actions: %s | uncertainties: %s"
                .formatted(result.analysisId(), spec.archetype(), spec.layout(), spec.components(),
                        spec.fieldHints(), spec.actions(), spec.uncertainties());
        try {
            ragService.ingestText("design-analysis-" + result.analysisId(), content, "design_analysis");
        } catch (Exception e) {
            log.warn("디자인 분석 RAG 인제스트 실패(DB 저장 완료): {}", e.getMessage());
        }
    }

    private UiDesignSpec analyzeWithTimeout(VisionAnalysisRequest request) {
        // 생성형 POST는 중복 실행 부작용이 있어 자동 retry하지 않는다. retry는 Figma GET에만 적용한다.
        try {
            return CompletableFuture.supplyAsync(() -> analyzeVisionOnce(request), visionExecutor)
                    .orTimeout(Math.max(1, properties.getTimeoutSeconds()), TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalStateException("비전 분석 호출에 실패했습니다.", cause);
        }
    }

    private UiDesignSpec analyzeVisionOnce(VisionAnalysisRequest request) {
        if (externalCallGuard == null) return visionAnalysisClient.analyze(request);
        ExternalDependency dependency = "openai".equalsIgnoreCase(visionAnalysisClient.providerId())
                ? ExternalDependency.OPENAI : ExternalDependency.OLLAMA;
        return externalCallGuard.execute(dependency, () -> visionAnalysisClient.analyze(request));
    }
}
