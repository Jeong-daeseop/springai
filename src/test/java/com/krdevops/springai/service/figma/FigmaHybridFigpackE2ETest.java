package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
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
import com.krdevops.springai.model.designsystem.ComponentBinding;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableBinding;
import com.krdevops.springai.model.figma.ComponentRegistrySnapshot;
import com.krdevops.springai.model.figma.DesignSystemProfileSnapshot;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportMetadata;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridApprovalRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidate;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridCandidateRequest;
import com.krdevops.springai.model.figma.hybrid.FigmaHybridExportResult;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.ScreenSpecificationService;
import com.krdevops.springai.service.WebCaptureAnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ARCH-0820: `.figpack` HYBRID를 candidate → revise → approve → export result 한 흐름으로 잇는다.
 *
 * <p>기존 {@link FigmaHybridExportServiceTest}는 각 단계를 대역 store로 따로 검증해서, 앞 단계가
 * 실제로 저장한 후보를 뒤 단계가 읽는지는 확인하지 못했다. 여기서는 실제
 * {@link FigmaHybridArtifactStore}(파일시스템)를 써서 단계 간 인계를 증명하고, 승인 직전
 * 명세 버전이 밀렸을 때 승인을 거부하는 가드도 서비스 수준에서 직접 검증한다.
 */
class FigmaHybridFigpackE2ETest {

    @TempDir Path artifactBasePath;

    private final DesignArtifactService artifactService = mock(DesignArtifactService.class);
    private final WebCaptureAnalysisService analysisService = mock(WebCaptureAnalysisService.class);
    private final ScreenSpecificationService screenSpecificationService = mock(ScreenSpecificationService.class);
    private final FigmaScreenExportService figmaScreenExportService = mock(FigmaScreenExportService.class);

    private String artifactId;
    private FigmaHybridArtifactStore store;
    private FigmaHybridExportService service;

    @BeforeEach
    void setUp() throws Exception {
        artifactId = UUID.randomUUID().toString();
        Files.createDirectory(artifactBasePath.resolve(artifactId));
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(artifactBasePath);
        store = new FigmaHybridArtifactStore(properties, new ObjectMapper().findAndRegisterModules());
        service = new FigmaHybridExportService(
                artifactService, analysisService, screenSpecificationService, figmaScreenExportService, store);

        when(artifactService.readDocument(artifactId)).thenReturn(document());
        when(analysisService.analyze(eq(artifactId), any())).thenReturn(analysis());
    }

    @Test
    void candidateRevisionAndApprovalShareTheSamePersistedArtifact() {
        ScreenSpecification proposed = spec(3, ScreenSpecStatus.REVIEW_REQUIRED);
        ScreenSpecification revised = spec(4, ScreenSpecStatus.REVIEW_REQUIRED);
        when(screenSpecificationService.create(
                eq("ebt"), eq("COMTNEMPLYRINFO"), eq("사용자 목록"), eq("crud"), any(), any(), any()))
                .thenReturn(proposed);
        when(screenSpecificationService.revise(any())).thenReturn(revised);
        when(screenSpecificationService.get(proposed.id())).thenReturn(revised);
        when(screenSpecificationService.approve(proposed.id()))
                .thenReturn(spec(4, ScreenSpecStatus.APPROVED));
        FigmaExportBundle semantic = exportResult();
        when(figmaScreenExportService.exportBundle(any())).thenReturn(semantic);
        FigmaImportArtifact referenceArtifact = new FigmaImportArtifact(
                artifactId, artifactId + ".figpack", artifactBasePath.resolve("reference.figpack").toString(), 10);
        when(artifactService.prepareFigmaImport(artifactId)).thenReturn(referenceArtifact);

        FigmaHybridCandidate candidate = service.createCandidate(candidateRequest());
        assertThat(candidate.humanApprovalRequired()).isTrue();
        assertThat(candidate.screenSpecification().version()).isEqualTo(3);

        // 사람이 Preview를 수정하면 후보가 새 명세 버전으로 갱신되어 같은 artifact에 다시 저장된다.
        FigmaHybridCandidate afterRevision = service.reviseCandidate(artifactId, proposed);
        assertThat(afterRevision.screenSpecification().version()).isEqualTo(4);
        assertThat(store.readCandidate(artifactId).screenSpecification().version()).isEqualTo(4);

        FigmaHybridExportResult result = service.approveAndExport(artifactId, approvalRequest(proposed.id()));

        // Reference(.figpack)와 Semantic 결과가 같은 artifact에 연결된 채 저장된다.
        assertThat(result.artifactId()).isEqualTo(artifactId);
        assertThat(result.referenceArtifact()).isEqualTo(referenceArtifact);
        assertThat(result.approvedScreenSpecification().status()).isEqualTo(ScreenSpecStatus.APPROVED);
        assertThat(result.approvedScreenSpecification().version()).isEqualTo(4);
        assertThat(result.report().semanticScreenId()).isEqualTo("list");
        assertThat(result.semanticResult().metadata().origin()).isEqualTo(FigmaExportMetadata.Origin.HYBRID);
        assertThat(store.findResult(artifactId)).contains(result);
    }

    /** 후보를 만든 뒤 명세 버전이 따로 올라갔다면 승인을 거부하고 아무 결과도 저장하지 않는다. */
    @Test
    void approvalIsRejectedWhenSpecificationVersionMovedAfterPreview() {
        ScreenSpecification proposed = spec(3, ScreenSpecStatus.REVIEW_REQUIRED);
        when(screenSpecificationService.create(
                eq("ebt"), eq("COMTNEMPLYRINFO"), eq("사용자 목록"), eq("crud"), any(), any(), any()))
                .thenReturn(proposed);
        when(screenSpecificationService.get(proposed.id())).thenReturn(spec(5, ScreenSpecStatus.REVIEW_REQUIRED));

        service.createCandidate(candidateRequest());

        assertThatThrownBy(() -> service.approveAndExport(artifactId, approvalRequest(proposed.id())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("버전이 변경되었습니다");
        assertThat(store.findResult(artifactId)).isEmpty();
    }

    /** 후보와 다른 명세를 승인하려 하면 거부한다. */
    @Test
    void approvalIsRejectedWhenSpecificationDoesNotMatchCandidate() {
        when(screenSpecificationService.create(
                eq("ebt"), eq("COMTNEMPLYRINFO"), eq("사용자 목록"), eq("crud"), any(), any(), any()))
                .thenReturn(spec(3, ScreenSpecStatus.REVIEW_REQUIRED));

        service.createCandidate(candidateRequest());

        assertThatThrownBy(() -> service.approveAndExport(artifactId, approvalRequest("spec-other")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Hybrid 후보와 일치하지 않습니다");
        assertThat(store.findResult(artifactId)).isEmpty();
    }

    private FigmaHybridCandidateRequest candidateRequest() {
        return new FigmaHybridCandidateRequest(
                artifactId, "ebt", "COMTNEMPLYRINFO", "사용자 목록", "crud",
                List.of("userId", "userName"), List.of("userId"));
    }

    private FigmaHybridApprovalRequest approvalRequest(String specificationId) {
        return new FigmaHybridApprovalRequest(
                specificationId, "list", "krds", null,
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW, true);
    }

    private FigmaExportBundle exportResult() {
        FigmaNodeSpec root = new FigmaNodeSpec(
                "list-root", FigmaNodeSpec.NodeType.PAGE, "egov.listPage", Map.of(), List.of());
        FigmaScreenSpec semanticSpec = new FigmaScreenSpec(
                "list", 4, "spec-user-management", 4,
                FigmaScreenType.LIST, LayoutPattern.STANDARD,
                "사용자 목록", null, "DESKTOP", "APPROVED",
                new FigmaScreenSpec.DesignSystemRef("krds", "1.0", "2026.07"),
                root, List.of());
        return new FigmaExportBundle(
                semanticSpec,
                new DesignSystemProfileSnapshot(sampleProfile(), LocalDateTime.now()),
                new ComponentRegistrySnapshot(sampleRegistry(), LocalDateTime.now()),
                new FigmaExportMetadata(LocalDateTime.now(), FigmaScreenSpec.SCHEMA_VERSION, 4, "1.0", "2026.07"));
    }

    private DesignSystemProfile sampleProfile() {
        return new DesignSystemProfile(
                "krds", "KRDS Design System", "1.0", "2026.07", "KRDS_FIGMA_LIBRARY_FILE_KEY",
                DesignSystemProfile.Status.PUBLISHED,
                Map.of("krds.button", new ComponentBinding("FIGMA_BUTTON_KEY", ComponentBinding.BindingStatus.BOUND)),
                Map.of("color.primary", new VariableBinding(
                        "VAR_COLOR_PRIMARY", "Colors", ComponentBinding.BindingStatus.BOUND)));
    }

    private ComponentRegistry sampleRegistry() {
        return new ComponentRegistry(
                "krds", "1.0", "2026.07",
                new ComponentRegistry.LibraryRef("KRDS_FIGMA_LIBRARY_FILE_KEY", "KRDS Design System"),
                Map.of("krds.button", new ComponentRegistryEntry(
                        "FIGMA_BUTTON_COMPONENT_SET_KEY",
                        Map.of("label", new ComponentRegistryEntry.PropertyMapping(
                                "Label", ComponentRegistryEntry.PropertyType.TEXT, null)))));
    }

    private ScreenSpecification spec(int version, ScreenSpecStatus status) {
        ScreenSpecification base = FigmaBuilderTestFixtures.userManagementSpec();
        return new ScreenSpecification(
                base.id(), version, status, base.screenName(), base.featureType(), base.archetype(),
                base.database(), base.primaryTable(), base.dataSources(), base.pages(), base.issues(),
                base.layoutDensity(), base.formColumnLayout(), base.actionPlacement(),
                base.searchPanelPlacement(), base.createdAt());
    }

    private DesignAnalysisResult analysis() {
        UiDesignSpec uiSpec = uiSpec();
        return DesignAnalysisResult.webCapture(
                "analysis", "cache",
                new WebCaptureDesignSourceMetadata(
                        artifactId, "document-key", "content-hash", "rendered-design-document-v1"),
                "mapper-v2", "crud", uiSpec, uiSpec.uncertainties(), LocalDateTime.now());
    }

    private UiDesignSpec uiSpec() {
        return new UiDesignSpec(
                "CRUD_LIST",
                new UiDesignSpec.LayoutSpec("WEB", "WIDE", "STANDARD", "single-column",
                        "top-right", "above-table"),
                List.of(new UiDesignSpec.ComponentSpec("TABLE", List.of("userName"))),
                List.of(new UiDesignSpec.ActionSpec("SEARCH", "PRIMARY")),
                List.of(new UiDesignSpec.FieldHint("userName", "사용자명", UiFieldRole.TITLE, "TEXT", 0.9)),
                Map.of("color.primary", "#246BEB"), List.of(), List.of());
    }

    private RenderedDesignDocument document() {
        RenderedNode node = new RenderedNode(
                "field-userName", null, "TEXT", "label", "text", "사용자명", "사용자명", null, true,
                new RenderedNode.Bounds(10, 10, 100, 30), Map.of("color", "#111111"), List.of());
        return new RenderedDesignDocument(
                RenderedDesignDocument.SCHEMA_VERSION, artifactId, "document-key", "content-hash",
                new RenderedDesignDocument.Source(
                        "URL", "SPRING", "http://localhost/users", "http://localhost/users",
                        "fingerprint", "2026-07-27T10:00:00"),
                new RenderedDesignDocument.Environment(
                        "DESKTOP", 1440, 900, 1, "ko-KR", "Asia/Seoul", "light", false, "chromium"),
                new RenderedDesignDocument.Page("사용자 목록", "root", 1440, 900, 0, 0, "#ffffff"),
                List.of(node), List.of(), Map.of("color.primary", "#246BEB"),
                List.of(new ComponentCandidate("TABLE", List.of("field-userName"), 0.95, List.of("table"))),
                List.of(), List.of(), Map.of("version", "1"));
    }
}
