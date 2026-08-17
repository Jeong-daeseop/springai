package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaExportMetadata;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaDesignOrchestrationServiceTest {

    @Test
    void approvedSpecificationProducesBundleArtifactBeforePreviewReady() {
        FigmaContextAnalyzer analyzer = mock(FigmaContextAnalyzer.class);
        FigmaFileAllowlistValidator allowlist = mock(FigmaFileAllowlistValidator.class);
        FigmaScreenExportService exportService = mock(FigmaScreenExportService.class);
        DesignArtifactService artifactService = mock(DesignArtifactService.class);
        FigmaDesignOperationRepository operationRepository = mock(FigmaDesignOperationRepository.class);
        FigmaExportBundle bundle = mock(FigmaExportBundle.class);
        FigmaDesignRequest request = FigmaDesignRequest.textDescription("사용자 목록", "allowed-file");
        FigmaScreenExportRequest exportRequest = exportRequest();

        when(analyzer.analyze("사용자 목록", null)).thenReturn(highConfidence());
        when(exportService.exportBundle(exportRequest)).thenReturn(bundle);
        when(bundle.metadata()).thenReturn(new FigmaExportMetadata(
                LocalDateTime.now(), "figma-screen-spec-v1", 1, "1.0.0", "registry-1"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(operationRepository.createOrReuse(request, exportRequest)).thenReturn(analyzed);
        when(artifactService.saveFigmaExportBundle(bundle)).thenReturn(
                new DesignArtifactService.FigmaBundleArtifact(
                        "user-list-v1-bundle", "figma-bundles/user-list/v1",
                        "user-list", 1, "a".repeat(64), LocalDateTime.now()));

        when(operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq(analyzed.operationId()),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.PREVIEW_READY),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> operation(request, FigmaDesignOperationStatus.PREVIEW_READY,
                        invocation.getArgument(2), invocation.getArgument(4)));
        FigmaDesignOrchestrationService service = service(
                analyzer, allowlist, exportService, artifactService, operationRepository);
        var operation = service.processApprovedSpecificationRequest(request, exportRequest);

        assertThat(operation.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
        assertThat(operation.requestHash()).matches("^[a-f0-9]{64}$");
        assertThat(operation.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.artifactType()).isEqualTo("FIGMA_EXPORT_BUNDLE");
            assertThat(artifact.contentHash()).isEqualTo("a".repeat(64));
        });
        assertThat(operation.sourceRevision()).isNotNull();
        verify(allowlist).validateFileKey("allowed-file");
        verify(exportService).exportBundle(exportRequest);
        verify(artifactService).saveFigmaExportBundle(bundle);
    }

    @Test
    void lowConfidenceDoesNotGenerateBundleOrArtifact() {
        FigmaContextAnalyzer analyzer = mock(FigmaContextAnalyzer.class);
        FigmaScreenExportService exportService = mock(FigmaScreenExportService.class);
        DesignArtifactService artifactService = mock(DesignArtifactService.class);
        FigmaDesignOperationRepository operationRepository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.textDescription("모호한 화면", "allowed-file");
        when(analyzer.analyze("모호한 화면", null)).thenReturn(
                FigmaContextAnalyzer.FigmaContextAnalysis.uncertain("분석 불확실"));

        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(operationRepository.createOrReuse(request, exportRequest())).thenReturn(analyzed);
        when(operationRepository.appendTransition(
                analyzed.operationId(), FigmaDesignOperationStatus.REJECTED,
                null, List.of(new com.krdevops.springai.model.contract.GenerationIssue(
                        "LOW_CONFIDENCE", com.krdevops.springai.model.contract.GenerationIssue.Severity.WARNING,
                        "CONTEXT_ANALYSIS", null, "LLM 분석 신뢰도 낮음: 90.0%", null)), List.of()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));
        var operation = service(analyzer, mock(FigmaFileAllowlistValidator.class),
                exportService, artifactService, operationRepository)
                .processApprovedSpecificationRequest(request, exportRequest());

        assertThat(operation.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(operation.artifacts()).isEmpty();
        verify(exportService, never()).exportBundle(org.mockito.ArgumentMatchers.any());
        verify(artifactService, never()).saveFigmaExportBundle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void advancedRequestIsPersistedAsAnalyzedUntilSpecificationApproval() {
        FigmaContextAnalyzer analyzer = mock(FigmaContextAnalyzer.class);
        FigmaFileAllowlistValidator allowlist = mock(FigmaFileAllowlistValidator.class);
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 화면과 같은 목록", "allowed-file", List.of("1:2"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(analyzer.analyze(request.prompt(), null)).thenReturn(highConfidence());
        when(repository.createOrReuse(request)).thenReturn(analyzed);

        var result = service(analyzer, allowlist, mock(FigmaScreenExportService.class),
                mock(DesignArtifactService.class), repository).processExplicitRequest(request);

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.ANALYZED);
        assertThat(result.artifacts()).isEmpty();
        verify(allowlist).validateFileKey("allowed-file");
        verify(repository).createOrReuse(request);
    }

    @Test
    void invalidAdvancedRequestIsRejectedBeforeRepositoryAccess() {
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.platformConvert(
                "반응형 변환", "allowed-file", List.of("1:2"), "WATCH");

        assertThatThrownBy(() -> service(mock(FigmaContextAnalyzer.class),
                mock(FigmaFileAllowlistValidator.class), mock(FigmaScreenExportService.class),
                mock(DesignArtifactService.class), repository).processExplicitRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetPlatform");
        verify(repository, never()).createOrReuse(org.mockito.ArgumentMatchers.any());
    }

    /**
     * 형식이 어긋난 editableNodeIds는 Operation이 저장되기 전에 거부해야 한다. 통과시키면 Apply
     * 시점 scope 재검증이 오탐 CONFLICT를 내는데, CONFLICT는 종단 상태이고 동일 requestHash를
     * createOrReuse가 재사용하므로 해당 요청이 영구히 복구 불가가 된다.
     */
    @Test
    void malformedEditableNodeIdIsRejectedBeforeRepositoryAccess() {
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.modifyExisting(
                "버튼 색상 변경", "allowed-file", List.of("node-789"));

        assertThatThrownBy(() -> service(mock(FigmaContextAnalyzer.class),
                mock(FigmaFileAllowlistValidator.class), mock(FigmaScreenExportService.class),
                mock(DesignArtifactService.class), repository).processExplicitRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId");
        verify(repository, never()).createOrReuse(org.mockito.ArgumentMatchers.any());
    }

    /** URL 표기(1-2)로 들어와도 REST 표기(1:2)로 정규화해 저장한다. */
    @Test
    void urlStyleEditableNodeIdIsNormalizedBeforePersisting() {
        FigmaContextAnalyzer analyzer = mock(FigmaContextAnalyzer.class);
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest incoming = FigmaDesignRequest.modifyExisting(
                "버튼 색상 변경", "allowed-file", List.of("1-2", "3:4"));
        FigmaDesignRequest expected = FigmaDesignRequest.modifyExisting(
                "버튼 색상 변경", "allowed-file", List.of("1:2", "3:4"));
        when(analyzer.analyze(incoming.prompt(), null)).thenReturn(highConfidence());
        when(repository.createOrReuse(expected))
                .thenReturn(operation(expected, FigmaDesignOperationStatus.ANALYZED, List.of()));

        var result = service(analyzer, mock(FigmaFileAllowlistValidator.class),
                mock(FigmaScreenExportService.class), mock(DesignArtifactService.class), repository)
                .processExplicitRequest(incoming);

        assertThat(result.request().editableNodeIds()).containsExactly("1:2", "3:4");
        verify(repository).createOrReuse(expected);
    }

    /**
     * R6-041: editableNodeIds만 정규화되고 referenceNodeIds는 형식 검증 없이 저장되던 격차를
     * 막는다. 형식이 어긋난 referenceNodeIds도 Operation이 저장되기 전에 거부해야 한다.
     */
    @Test
    void malformedReferenceNodeIdIsRejectedBeforeRepositoryAccess() {
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 화면과 같은 목록", "allowed-file", List.of("not-a-node-id"));

        assertThatThrownBy(() -> service(mock(FigmaContextAnalyzer.class),
                mock(FigmaFileAllowlistValidator.class), mock(FigmaScreenExportService.class),
                mock(DesignArtifactService.class), repository).processExplicitRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId");
        verify(repository, never()).createOrReuse(org.mockito.ArgumentMatchers.any());
    }

    /** R6-041: imageNodeIds도 referenceNodeIds/editableNodeIds와 동일한 규칙으로 검증해야 한다. */
    @Test
    void malformedImageNodeIdIsRejectedBeforeRepositoryAccess() {
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.imageReference(
                "이미지 기반 생성", "allowed-file", List.of("123"));

        assertThatThrownBy(() -> service(mock(FigmaContextAnalyzer.class),
                mock(FigmaFileAllowlistValidator.class), mock(FigmaScreenExportService.class),
                mock(DesignArtifactService.class), repository).processExplicitRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId");
        verify(repository, never()).createOrReuse(org.mockito.ArgumentMatchers.any());
    }

    /** URL 표기(1-2)로 들어온 referenceNodeIds도 REST 표기(1:2)로 정규화해 저장한다. */
    @Test
    void urlStyleReferenceNodeIdIsNormalizedBeforePersisting() {
        FigmaContextAnalyzer analyzer = mock(FigmaContextAnalyzer.class);
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest incoming = FigmaDesignRequest.referenceStyle(
                "기존 화면과 같은 목록", "allowed-file", List.of("1-2"));
        FigmaDesignRequest expected = FigmaDesignRequest.referenceStyle(
                "기존 화면과 같은 목록", "allowed-file", List.of("1:2"));
        when(analyzer.analyze(incoming.prompt(), null)).thenReturn(highConfidence());
        when(repository.createOrReuse(expected))
                .thenReturn(operation(expected, FigmaDesignOperationStatus.ANALYZED, List.of()));

        var result = service(analyzer, mock(FigmaFileAllowlistValidator.class),
                mock(FigmaScreenExportService.class), mock(DesignArtifactService.class), repository)
                .processExplicitRequest(incoming);

        assertThat(result.request().referenceNodeIds()).containsExactly("1:2");
        verify(repository).createOrReuse(expected);
    }

    private FigmaDesignOrchestrationService service(
            FigmaContextAnalyzer analyzer,
            FigmaFileAllowlistValidator allowlist,
            FigmaScreenExportService exportService,
            DesignArtifactService artifactService,
            FigmaDesignOperationRepository operationRepository) {
        return new FigmaDesignOrchestrationService(
                analyzer, allowlist, exportService, artifactService, operationRepository);
    }

    private FigmaScreenExportRequest exportRequest() {
        return new FigmaScreenExportRequest(
                "spec-user", 1, "user-list", "ftc-krds", "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW);
    }

    private FigmaContextAnalyzer.FigmaContextAnalysis highConfidence() {
        return new FigmaContextAnalyzer.FigmaContextAnalysis(
                "user", com.krdevops.springai.model.figma.FigmaScreenType.LIST,
                com.krdevops.springai.model.figma.LayoutPattern.STANDARD,
                List.of("krds.table"), 0.1, "명확함", false);
    }

    private com.krdevops.springai.model.figma.contract.FigmaDesignOperation operation(
            FigmaDesignRequest request, FigmaDesignOperationStatus status,
            List<com.krdevops.springai.model.contract.ArtifactRef> artifacts) {
        return operation(request, status, null, artifacts);
    }

    private com.krdevops.springai.model.figma.contract.FigmaDesignOperation operation(
            FigmaDesignRequest request, FigmaDesignOperationStatus status,
            com.krdevops.springai.model.contract.SourceRevisionRef sourceRevision,
            List<com.krdevops.springai.model.contract.ArtifactRef> artifacts) {
        Instant now = Instant.now();
        return new com.krdevops.springai.model.figma.contract.FigmaDesignOperation(
                "figop-test", status == FigmaDesignOperationStatus.ANALYZED ? 1 : 2,
                request, "b".repeat(64), status, sourceRevision, List.of(), artifacts, now, now);
    }
}
