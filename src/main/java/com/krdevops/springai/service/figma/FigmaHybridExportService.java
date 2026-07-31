package com.krdevops.springai.service.figma;

import com.krdevops.springai.model.capture.FigmaImportArtifact;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridApprovalRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidateRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridExportResult;
import com.krdevops.springai.model.figma.hybrid.HybridConversionReport;
import com.krdevops.springai.model.figma.hybrid.HybridDecisionField;
import com.krdevops.springai.model.figma.hybrid.HybridFieldSource;
import com.krdevops.springai.model.figma.hybrid.HybridReferenceSnapshot;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.ScreenSpecificationService;
import com.krdevops.springai.service.WebCaptureAnalysisService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * R7: 동일 capture artifact에서 .figpack Reference와 승인 기반 Semantic 출력을 연결한다.
 * 의미 추론은 Figma Reference Node가 아니라 원본 document.json만 사용한다.
 */
@Service
public class FigmaHybridExportService {

    private final DesignArtifactService artifactService;
    private final WebCaptureAnalysisService analysisService;
    private final ScreenSpecificationService screenSpecificationService;
    private final FigmaScreenExportService figmaScreenExportService;
    private final FigmaHybridArtifactStore artifactStore;

    public FigmaHybridExportService(
            DesignArtifactService artifactService,
            WebCaptureAnalysisService analysisService,
            ScreenSpecificationService screenSpecificationService,
            FigmaScreenExportService figmaScreenExportService,
            FigmaHybridArtifactStore artifactStore
    ) {
        this.artifactService = artifactService;
        this.analysisService = analysisService;
        this.screenSpecificationService = screenSpecificationService;
        this.figmaScreenExportService = figmaScreenExportService;
        this.artifactStore = artifactStore;
    }

    public FigmaHybridCandidate createCandidate(FigmaHybridCandidateRequest request) {
        artifactService.get(request.artifactId());
        RenderedDesignDocument document = artifactService.readDocument(request.artifactId());
        DesignAnalysisResult analysis = analysisService.analyze(request.artifactId(), request.featureType());
        UiDesignSpec uiSpec = analysis.uiSpec();

        ScreenSpecification specification = screenSpecificationService.create(
                request.database(), request.primaryTable(), request.screenName(),
                request.featureType(), uiSpec, request.listColumns(), request.detailColumns());

        FigmaHybridCandidate candidate = candidate(
                request.artifactId(), document, uiSpec, specification, analysis.warnings());
        artifactStore.saveCandidate(candidate);
        return candidate;
    }

    public FigmaHybridCandidate getCandidate(String artifactId) {
        return artifactStore.readCandidate(artifactId);
    }

    /**
     * 사람의 Preview 수정본을 정식 ScreenSpecification 새 버전으로 저장하고 후보 연결을 갱신한다.
     */
    public FigmaHybridCandidate reviseCandidate(String artifactId, ScreenSpecification revised) {
        FigmaHybridCandidate current = artifactStore.readCandidate(artifactId);
        if (!current.screenSpecification().id().equals(revised.id())) {
            throw new IllegalArgumentException(
                    "수정 명세가 Hybrid 후보와 일치하지 않습니다: " + revised.id());
        }
        ScreenSpecification saved = screenSpecificationService.revise(revised);
        RenderedDesignDocument document = artifactService.readDocument(artifactId);
        DesignAnalysisResult analysis = analysisService.analyze(artifactId, saved.featureType());
        FigmaHybridCandidate updated = candidate(
                artifactId, document, analysis.uiSpec(), saved, analysis.warnings());
        artifactStore.saveCandidate(updated);
        return updated;
    }

    /**
     * 사람의 명시적 승인 후에만 승인본 저장과 FigmaScreenSpec 생성을 연속 수행한다.
     */
    public FigmaHybridExportResult approveAndExport(
            String artifactId,
            FigmaHybridApprovalRequest request
    ) {
        FigmaHybridCandidate candidate = artifactStore.readCandidate(artifactId);
        if (!candidate.screenSpecification().id().equals(request.screenSpecificationId())) {
            throw new IllegalArgumentException(
                    "승인 요청 명세가 Hybrid 후보와 일치하지 않습니다: "
                            + request.screenSpecificationId());
        }

        ScreenSpecification latest = screenSpecificationService.get(request.screenSpecificationId());
        if (latest.version() != candidate.screenSpecification().version()) {
            throw new IllegalArgumentException(
                    "Preview 이후 ScreenSpecification 버전이 변경되었습니다. 후보를 다시 조회하십시오: "
                            + candidate.screenSpecification().version() + " -> " + latest.version());
        }
        ScreenSpecification approved =
                screenSpecificationService.approve(request.screenSpecificationId());
        FigmaExportResult semantic = figmaScreenExportService.export(
                new FigmaScreenExportRequest(
                        approved.id(), approved.version(), request.pageId(),
                        request.designSystemProfileId(), semanticViewport(candidate, request.viewport()),
                        request.exportMode(), request.syncMode()));
        if (semantic.figmaScreenSpec() == null) {
            throw new IllegalStateException("승인 후 FigmaScreenSpec 생성 결과가 없습니다.");
        }

        FigmaImportArtifact referenceArtifact = artifactService.prepareFigmaImport(artifactId);
        HybridConversionReport report = candidate.report().withSemanticOutput(
                semantic.figmaScreenSpec().screenId(),
                semantic.figmaScreenSpec().screenVersion(),
                semantic.figmaScreenSpec().viewport());
        FigmaHybridExportResult result = new FigmaHybridExportResult(
                artifactId, referenceArtifact, approved, semantic, report, LocalDateTime.now());
        artifactStore.saveResult(result);
        return result;
    }

    public FigmaHybridExportResult getResult(String artifactId) {
        return artifactStore.findResult(artifactId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Hybrid Semantic 결과를 찾을 수 없습니다: " + artifactId));
    }

    private FigmaHybridCandidate candidate(
            String artifactId,
            RenderedDesignDocument document,
            UiDesignSpec uiSpec,
            ScreenSpecification specification,
            List<String> analysisWarnings
    ) {
        HybridReferenceSnapshot reference = reference(document);
        List<HybridDecisionField> decisions = decisions(uiSpec, specification, document);
        HybridConversionReport report =
                report(artifactId, document, uiSpec, specification, analysisWarnings, reference.viewport());
        return new FigmaHybridCandidate(
                artifactId, reference, specification, decisions, report, true, LocalDateTime.now());
    }

    private HybridReferenceSnapshot reference(RenderedDesignDocument document) {
        RenderedDesignDocument.Source source = document.source();
        RenderedDesignDocument.Environment environment = document.environment();
        List<String> textNodeIds = document.nodes().stream()
                .filter(RenderedNode::visible)
                .filter(node -> !blank(node.text()) || !blank(node.label()))
                .map(RenderedNode::id)
                .toList();
        List<String> styleNodeIds = document.nodes().stream()
                .filter(node -> !node.styles().isEmpty())
                .map(RenderedNode::id)
                .toList();
        return new HybridReferenceSnapshot(
                document.captureId(), "REFERENCE_SNAPSHOT", "source.figpack", "document.json",
                artifactService.hasPreview(document.captureId()) ? "preview.png" : null,
                document.documentKey(), document.contentHash(), document.schemaVersion(),
                source == null ? null : source.requestedUrl(),
                source == null ? null : source.finalUrl(),
                source == null ? null : source.capturedAt(),
                environment == null ? null : environment.viewportName(),
                environment == null ? 0 : environment.viewportWidth(),
                environment == null ? 0 : environment.viewportHeight(),
                textNodeIds, styleNodeIds, document.tokens().keySet().stream().sorted().toList());
    }

    private List<HybridDecisionField> decisions(
            UiDesignSpec uiSpec,
            ScreenSpecification specification,
            RenderedDesignDocument document
    ) {
        List<HybridDecisionField> fields = new ArrayList<>();
        fields.add(decision("screenSpecification.database", specification.database(),
                HybridFieldSource.USER_INPUT, 1.0, true,
                "캡처만으로 실제 DB schema를 확정할 수 없습니다.", List.of()));
        fields.add(decision("screenSpecification.primaryTable", specification.primaryTable(),
                HybridFieldSource.USER_INPUT, 1.0, true,
                "캡처만으로 실제 주 테이블을 확정할 수 없습니다.", List.of()));
        fields.add(decision("screenSpecification.screenName", specification.screenName(),
                HybridFieldSource.USER_INPUT, 1.0, true,
                "업무 화면명은 사람이 확인해야 합니다.", List.of()));
        fields.add(decision("screenSpecification.archetype", uiSpec.archetype(),
                HybridFieldSource.CAPTURE_INFERENCE,
                uiSpec.uncertainties().isEmpty() ? 0.9 : 0.6,
                !uiSpec.uncertainties().isEmpty(),
                "document.json의 구성요소와 필드 패턴에서 추론했습니다.",
                document.componentCandidates().stream().flatMap(candidate -> candidate.nodeIds().stream()).toList()));
        for (UiDesignSpec.FieldHint hint : uiSpec.fieldHints()) {
            fields.add(decision("uiDesignSpec.fieldHints." + hint.id(), hint.label(),
                    HybridFieldSource.CAPTURE_INFERENCE, hint.confidence(),
                    hint.confidence() < 0.7,
                    hint.confidence() < 0.7
                            ? "신뢰도가 기준값 미만이므로 사람 확인이 필요합니다."
                            : "원본 document.json의 label/control에서 추론했습니다.",
                    sourceNodes(document, hint.id(), hint.label())));
        }
        specification.pages().stream()
                .flatMap(page -> page.fields().stream())
                .forEach(binding -> fields.add(decision(
                        "screenSpecification.binding." + binding.id(),
                        binding.source() == null ? null
                                : binding.source().tableAlias() + "." + binding.source().column(),
                        HybridFieldSource.DATABASE_SCHEMA, binding.confidence(),
                        binding.source() == null || binding.confidence() < 0.7,
                        "DB Schema와 캡처 FieldHint를 결합한 Binding입니다.",
                        sourceNodes(document, binding.id(), binding.label()))));
        return List.copyOf(fields);
    }

    private HybridConversionReport report(
            String artifactId,
            RenderedDesignDocument document,
            UiDesignSpec uiSpec,
            ScreenSpecification specification,
            List<String> analysisWarnings,
            String referenceViewport
    ) {
        Set<String> semanticFieldIds = new HashSet<>();
        specification.pages().forEach(page ->
                page.fields().forEach(field -> semanticFieldIds.add(normalize(field.id()))));
        List<String> unmapped = uiSpec.fieldHints().stream()
                .filter(hint -> !semanticFieldIds.contains(normalize(hint.id())))
                .map(UiDesignSpec.FieldHint::id)
                .toList();
        int semanticFields = specification.pages().stream().mapToInt(page -> page.fields().size()).sum();
        int semanticActions = specification.pages().stream().mapToInt(page -> page.actions().size()).sum();
        int visibleText = (int) document.nodes().stream()
                .filter(RenderedNode::visible)
                .filter(node -> !blank(node.text()) || !blank(node.label()))
                .count();
        List<String> warnings = new ArrayList<>();
        if (analysisWarnings != null) {
            warnings.addAll(analysisWarnings);
        }
        warnings.addAll(uiSpec.uncertainties());
        if (!unmapped.isEmpty()) {
            warnings.add("ScreenSpecification에 매핑되지 않은 캡처 FieldHint가 있습니다: " + unmapped);
        }
        return new HybridConversionReport(
                artifactId, document.contentHash(), document.nodes().size(), visibleText,
                document.componentCandidates().size(), uiSpec.fieldHints().size(),
                specification.pages().size(), semanticFields, semanticActions,
                unmapped, warnings, referenceViewport, referenceViewport, true,
                null, null, LocalDateTime.now());
    }

    private List<String> sourceNodes(
            RenderedDesignDocument document,
            String id,
            String label
    ) {
        String normalizedId = normalize(id);
        String normalizedLabel = normalize(label);
        return document.nodes().stream()
                .filter(node -> normalize(node.id()).equals(normalizedId)
                        || normalize(node.label()).equals(normalizedLabel)
                        || normalize(node.text()).equals(normalizedLabel))
                .map(RenderedNode::id)
                .limit(10)
                .toList();
    }

    private HybridDecisionField decision(
            String path,
            String value,
            HybridFieldSource source,
            double confidence,
            boolean requiresHumanConfirmation,
            String reason,
            List<String> sourceNodeIds
    ) {
        return new HybridDecisionField(
                path, value, source, confidence, requiresHumanConfirmation, reason, sourceNodeIds);
    }

    private String semanticViewport(FigmaHybridCandidate candidate, String requested) {
        if (!blank(requested)) {
            return requested;
        }
        String referenceViewport = candidate.reference().viewport();
        return blank(referenceViewport) ? "DESKTOP" : referenceViewport;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9가-힣]", "").toLowerCase();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
