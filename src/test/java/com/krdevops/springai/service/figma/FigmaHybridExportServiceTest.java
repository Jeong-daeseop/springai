package com.krdevops.springai.service.figma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.krdevops.springai.model.capture.ComponentCandidate;
import com.krdevops.springai.model.capture.FigmaImportArtifact;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedNode;
import com.krdevops.springai.model.design.DesignAnalysisResult;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.UiFieldRole;
import com.krdevops.springai.model.design.WebCaptureDesignSourceMetadata;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridApprovalRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidateRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridExportResult;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.ScreenSpecificationService;
import com.krdevops.springai.service.WebCaptureAnalysisService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FigmaHybridExportServiceTest {

    @Mock
    private DesignArtifactService artifactService;
    @Mock
    private WebCaptureAnalysisService analysisService;
    @Mock
    private ScreenSpecificationService screenSpecificationService;
    @Mock
    private FigmaScreenExportService figmaScreenExportService;
    @Mock
    private FigmaHybridArtifactStore artifactStore;

    private FigmaHybridExportService service;

    @BeforeEach
    void setUp() {
        service = new FigmaHybridExportService(
                artifactService, analysisService, screenSpecificationService,
                figmaScreenExportService, artifactStore);
    }

    @Test
    void documentJson에서사람승인이필요한후보와출처보고서를만든다() {
        String artifactId = UUID.randomUUID().toString();
        RenderedDesignDocument document = document(artifactId);
        UiDesignSpec uiSpec = uiSpec();
        ScreenSpecification proposed =
                FigmaBuilderTestFixtures.userManagementSpec().withStatus(ScreenSpecStatus.REVIEW_REQUIRED);
        FigmaHybridCandidateRequest request = new FigmaHybridCandidateRequest(
                artifactId, "ebt", "COMTNEMPLYRINFO", "사용자 목록", "crud",
                List.of("userId", "userName"), List.of("userId"));

        when(artifactService.readDocument(artifactId)).thenReturn(document);
        when(analysisService.analyze(artifactId, "crud")).thenReturn(analysis(artifactId, uiSpec));
        when(screenSpecificationService.create(
                eq("ebt"), eq("COMTNEMPLYRINFO"), eq("사용자 목록"), eq("crud"),
                eq(uiSpec), any(), any())).thenReturn(proposed);

        FigmaHybridCandidate result = service.createCandidate(request);

        assertThat(result.artifactId()).isEqualTo(artifactId);
        assertThat(result.reference().artifactRole()).isEqualTo("REFERENCE_SNAPSHOT");
        assertThat(result.reference().figpackFile()).isEqualTo("source.figpack");
        assertThat(result.reference().documentFile()).isEqualTo("document.json");
        assertThat(result.reference().textSourceNodeIds()).contains("field-userName");
        assertThat(result.humanApprovalRequired()).isTrue();
        assertThat(result.decisions())
                .anySatisfy(decision -> {
                    assertThat(decision.path()).isEqualTo("screenSpecification.database");
                    assertThat(decision.requiresHumanConfirmation()).isTrue();
                });
        assertThat(result.report().sourceNodeCount()).isEqualTo(1);
        assertThat(result.report().semanticPageCount()).isEqualTo(2);
        verify(artifactStore).saveCandidate(result);
    }

    @Test
    void 사람의명시적승인후에만Reference와Semantic결과를같은artifact에연결한다() {
        String artifactId = UUID.randomUUID().toString();
        ScreenSpecification proposed =
                FigmaBuilderTestFixtures.userManagementSpec().withStatus(ScreenSpecStatus.REVIEW_REQUIRED);
        ScreenSpecification approved = proposed.withStatus(ScreenSpecStatus.APPROVED);
        FigmaHybridCandidate candidate = new FigmaHybridCandidate(
                artifactId,
                new com.krdevops.springai.model.figma.hybrid.HybridReferenceSnapshot(
                        artifactId, "REFERENCE_SNAPSHOT", "source.figpack", "document.json",
                        "preview.png", "doc", "hash", "v1", null, null, null,
                        "DESKTOP", 1440, 900, List.of(), List.of(), List.of()),
                proposed, List.of(),
                new com.krdevops.springai.model.figma.hybrid.HybridConversionReport(
                        artifactId, "hash", 1, 1, 1, 1, 2, 5, 7,
                        List.of(), List.of(), "DESKTOP", "DESKTOP", true,
                        null, null, null),
                true, null);
        FigmaScreenSpec semanticSpec = org.mockito.Mockito.mock(FigmaScreenSpec.class);
        when(semanticSpec.screenId()).thenReturn("list");
        when(semanticSpec.screenVersion()).thenReturn(4);
        when(semanticSpec.viewport()).thenReturn("DESKTOP");
        FigmaExportResult semantic =
                new FigmaExportResult(FigmaExportResult.Status.SUCCESS, semanticSpec, List.of(), null);
        FigmaImportArtifact reference =
                new FigmaImportArtifact(artifactId, artifactId + ".figpack", "/tmp/reference.figpack", 10);

        when(artifactStore.readCandidate(artifactId)).thenReturn(candidate);
        when(screenSpecificationService.get(proposed.id())).thenReturn(proposed);
        when(screenSpecificationService.approve(proposed.id())).thenReturn(approved);
        when(figmaScreenExportService.export(any())).thenReturn(semantic);
        when(artifactService.prepareFigmaImport(artifactId)).thenReturn(reference);

        FigmaHybridExportResult result = service.approveAndExport(
                artifactId,
                new FigmaHybridApprovalRequest(
                        proposed.id(), "list", "krds", null,
                        FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW, true));

        assertThat(result.artifactId()).isEqualTo(artifactId);
        assertThat(result.referenceArtifact()).isEqualTo(reference);
        assertThat(result.approvedScreenSpecification().status()).isEqualTo(ScreenSpecStatus.APPROVED);
        assertThat(result.report().semanticScreenId()).isEqualTo("list");
        assertThat(result.report().semanticScreenVersion()).isEqualTo(4);
        assertThat(result.report().viewportMatched()).isTrue();
        verify(artifactStore).saveResult(result);
    }

    @Test
    void 승인플래그가없으면승인요청자체를거부한다() {
        assertThatThrownBy(() -> new FigmaHybridApprovalRequest(
                "screen", "list", "krds", "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사람의 Preview 승인");
    }

    private DesignAnalysisResult analysis(String artifactId, UiDesignSpec uiSpec) {
        return DesignAnalysisResult.webCapture(
                "analysis", "cache",
                new WebCaptureDesignSourceMetadata(
                        artifactId, "document-key", "content-hash", "rendered-design-document-v1"),
                "mapper-v2", "crud", uiSpec, uiSpec.uncertainties(), LocalDateTime.now());
    }

    private UiDesignSpec uiSpec() {
        return new UiDesignSpec(
                "CRUD_LIST",
                new UiDesignSpec.LayoutSpec(
                        "WEB", "WIDE", "COMFORTABLE", "single-column",
                        "top-right", "above-table"),
                List.of(new UiDesignSpec.ComponentSpec("TABLE", List.of("userName"))),
                List.of(new UiDesignSpec.ActionSpec("SEARCH", "PRIMARY")),
                List.of(new UiDesignSpec.FieldHint(
                        "userName", "사용자명", UiFieldRole.TITLE, "TEXT", 0.9)),
                Map.of("color.primary", "#246BEB"), List.of(), List.of());
    }

    private RenderedDesignDocument document(String artifactId) {
        RenderedNode node = new RenderedNode(
                "field-userName", null, "TEXT", "label", "text",
                "사용자명", "사용자명", null, true,
                new RenderedNode.Bounds(10, 10, 100, 30),
                Map.of("color", "#111111"), List.of());
        return new RenderedDesignDocument(
                RenderedDesignDocument.SCHEMA_VERSION, artifactId, "document-key", "content-hash",
                new RenderedDesignDocument.Source(
                        "URL", "SPRING", "http://localhost/users", "http://localhost/users",
                        "fingerprint", "2026-07-27T10:00:00"),
                new RenderedDesignDocument.Environment(
                        "DESKTOP", 1440, 900, 1, "ko-KR", "Asia/Seoul",
                        "light", false, "chromium"),
                new RenderedDesignDocument.Page(
                        "사용자 목록", "root", 1440, 900, 0, 0, "#ffffff"),
                List.of(node), List.of(), Map.of("color.primary", "#246BEB"),
                List.of(new ComponentCandidate(
                        "TABLE", List.of("field-userName"), 0.95, List.of("table"))),
                List.of(), List.of(), Map.of("version", "1"));
    }
}
