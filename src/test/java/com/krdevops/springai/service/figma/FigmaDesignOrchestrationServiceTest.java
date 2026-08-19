package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.design.role.SemanticRole;
import com.krdevops.springai.model.figma.ComponentRegistrySnapshot;
import com.krdevops.springai.model.figma.DesignSystemProfileSnapshot;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaExportMetadata;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.model.figma.ResolvedComponentRef;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.model.figma.contract.FigmaDesignOperationStatus;
import com.krdevops.springai.model.figma.contract.FigmaDesignRequest;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.mapper.FigmaDesignOperationRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

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
        // R5-040: processApprovedSpecificationRequest는 저장 직전 operationId를 새겨 넣는다.
        when(bundle.withOperationId(org.mockito.ArgumentMatchers.any())).thenReturn(bundle);
        when(bundle.withOrigin(org.mockito.ArgumentMatchers.any())).thenReturn(bundle);
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
        verify(bundle).withOrigin(FigmaExportMetadata.Origin.ORCHESTRATED);
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

    /**
     * R6-T12: components는 승인된 논리 컴포넌트 타입이어야 한다 — 원시 Figma nodeId를 그대로
     * 넣으면 Registry의 논리 타입 allowlist를 우회할 수 있으므로 저장 전에 거부해야 한다.
     */
    @Test
    void componentSpecifiedRejectsRawFigmaNodeIdInsteadOfLogicalType() {
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.componentSpecified(
                "버튼과 표", "allowed-file", List.of("krds.button", "1:234"));

        assertThatThrownBy(() -> service(mock(FigmaContextAnalyzer.class),
                mock(FigmaFileAllowlistValidator.class), mock(FigmaScreenExportService.class),
                mock(DesignArtifactService.class), repository).processExplicitRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1:234");
        verify(repository, never()).createOrReuse(org.mockito.ArgumentMatchers.any());
    }

    /** 논리 컴포넌트 타입만 있는 정상 요청은 그대로 통과한다. */
    @Test
    void componentSpecifiedAcceptsLogicalTypesOnly() {
        FigmaContextAnalyzer analyzer = mock(FigmaContextAnalyzer.class);
        FigmaDesignOperationRepository repository = mock(FigmaDesignOperationRepository.class);
        FigmaDesignRequest request = FigmaDesignRequest.componentSpecified(
                "버튼과 표", "allowed-file", List.of("krds.button", "krds.table"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(analyzer.analyze(request.prompt(), null)).thenReturn(highConfidence());
        when(repository.createOrReuse(request)).thenReturn(analyzed);

        var result = service(analyzer, mock(FigmaFileAllowlistValidator.class),
                mock(FigmaScreenExportService.class), mock(DesignArtifactService.class), repository)
                .processExplicitRequest(request);

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.ANALYZED);
        verify(repository).createOrReuse(request);
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

    // ===== R6-032~038(2026-08-18): generateBundle() — DB 바인딩 기반 실제 Bundle 생성 =====

    @Test
    void generateBundleForReferenceStyleAnalyzesAndExportsApprovedSpecification() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"),
                "ebt", "emp_list", "직원 목록", "crud");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        var analysisResult = new com.krdevops.springai.model.design.DesignAnalysisResult(
                "analysis-1", "hash", null, null, "figma", "deterministic-mapper", "v1",
                List.of(), null, List.of(), LocalDateTime.now());
        when(deps.designReferenceAnalysisService.analyzeFigma(
                "https://www.figma.com/design/allowed-file/reference", "1:2", "crud"))
                .thenReturn(analysisResult);
        var spec = approvedSpecification();
        when(deps.screenSpecificationService.create("ebt", "emp_list", "직원 목록", "crud", null))
                .thenReturn(spec);
        var bundle = mock(FigmaExportBundle.class);
        when(bundle.metadata()).thenReturn(new FigmaExportMetadata(
                LocalDateTime.now(), "figma-screen-spec-v1", 1, "1.0.0", "registry-1"));
        when(bundle.withOperationId("figop-test")).thenReturn(bundle);
        when(bundle.withOrigin(org.mockito.ArgumentMatchers.any())).thenReturn(bundle);
        when(deps.exportService.exportBundle(new FigmaScreenExportRequest(
                spec.id(), spec.version(), "emp_list_LIST", null, "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW))).thenReturn(bundle);
        when(deps.artifactService.saveFigmaExportBundle(bundle)).thenReturn(
                new DesignArtifactService.FigmaBundleArtifact(
                        "emp-list-v1-bundle", "figma-bundles/emp-list/v1",
                        "emp-list", 1, "a".repeat(64), LocalDateTime.now()));
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.PREVIEW_READY),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.PREVIEW_READY, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
        verify(deps.artifactService).saveFigmaExportBundle(bundle);
        verify(bundle).withOrigin(FigmaExportMetadata.Origin.ORCHESTRATED);
    }

    /**
     * 22/23번 문서 PROP-01/S-01(경로 A): DB 미지정이어도 분석은 그대로 실행해 필드 후보를 뽑고,
     * 그 후보를 issues에 실어 AWAITING_TABLE_BINDING으로 전이한다("디자인 먼저, 테이블은 나중").
     */
    @Test
    void generateBundleForReferenceStyleWithoutDatabaseStillAnalyzesAndAwaitsTableBindingWithCandidates() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        var uiSpec = new com.krdevops.springai.model.design.UiDesignSpec(
                "CRUD_LIST", null, List.of(), List.of(),
                List.of(
                        new com.krdevops.springai.model.design.UiDesignSpec.FieldHint(
                                "title", "제목", com.krdevops.springai.model.design.UiFieldRole.TITLE, "TEXT", 0.9),
                        new com.krdevops.springai.model.design.UiDesignSpec.FieldHint(
                                "author", "작성자", com.krdevops.springai.model.design.UiFieldRole.AUTHOR, "TEXT", 0.8)),
                java.util.Map.of(), List.of(), List.of());
        var analysisResult = new com.krdevops.springai.model.design.DesignAnalysisResult(
                "analysis-1", "hash", null, null, "figma", "deterministic-mapper", "v1",
                List.of(), uiSpec, List.of(), LocalDateTime.now());
        when(deps.designReferenceAnalysisService.analyzeFigma(
                "https://www.figma.com/design/allowed-file/reference", "1:2", null))
                .thenReturn(analysisResult);
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.AWAITING_TABLE_BINDING),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.AWAITING_TABLE_BINDING, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.AWAITING_TABLE_BINDING);
        assertThat(issuesCaptor.getValue()).hasSize(3);
        assertThat(issuesCaptor.getValue().get(0).code()).isEqualTo("DATABASE_TABLE_PENDING");
        assertThat(issuesCaptor.getValue()).filteredOn(issue -> issue.code().equals("FIELD_CANDIDATE"))
                .extracting(com.krdevops.springai.model.contract.GenerationIssue::sourceLocation)
                .containsExactly("title", "author");
        verify(deps.designReferenceAnalysisService).analyzeFigma(
                "https://www.figma.com/design/allowed-file/reference", "1:2", null);
    }

    /**
     * 22/23번 문서 PROP-01/S-01(경로 A): IMAGE_REFERENCE도 REFERENCE_STYLE과 동일하게 분석은
     * 실행하되 database/tableName이 없으면 AWAITING_TABLE_BINDING으로 미룬다.
     */
    @Test
    void generateBundleForImageReferenceWithoutDatabaseStillAnalyzesAndAwaitsTableBindingWithCandidates()
            throws Exception {
        var deps = bundleGenerationDeps();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.png", exchange -> {
            byte[] body = {1, 2, 3, 4};
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String imageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/image.png";
            FigmaDesignRequest request = FigmaDesignRequest.imageReference(
                    "이미지처럼", "allowed-file", List.of("9:9"), null, null, "직원 목록", "crud");
            var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
            when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
            when(deps.figmaApiClient.queryImages("allowed-file", List.of("9:9"))).thenReturn(
                    new com.krdevops.springai.service.FigmaApiClient.FigmaImageUrls(
                            java.util.Map.of("9:9", imageUrl), List.of(), java.time.Instant.now()));
            var uiSpec = new com.krdevops.springai.model.design.UiDesignSpec(
                    "CRUD_LIST", null, List.of(), List.of(),
                    List.of(new com.krdevops.springai.model.design.UiDesignSpec.FieldHint(
                            "title", "제목", com.krdevops.springai.model.design.UiFieldRole.TITLE, "TEXT", 0.9)),
                    java.util.Map.of(), List.of(), List.of());
            var analysisResult = new com.krdevops.springai.model.design.DesignAnalysisResult(
                    "analysis-2", "hash", null, null, "vision", "gpt-4o-mini", "v1",
                    List.of(), uiSpec, List.of(), LocalDateTime.now());
            when(deps.designReferenceAnalysisService.analyze(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq("crud")))
                    .thenReturn(analysisResult);
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                    org.mockito.ArgumentCaptor.forClass(List.class);
            when(deps.operationRepository.appendTransition(
                    org.mockito.ArgumentMatchers.eq("figop-test"),
                    org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.AWAITING_TABLE_BINDING),
                    org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                    org.mockito.ArgumentMatchers.anyList()))
                    .thenReturn(operation(request, FigmaDesignOperationStatus.AWAITING_TABLE_BINDING, List.of()));

            var result = deps.service.generateBundle("figop-test");

            assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.AWAITING_TABLE_BINDING);
            assertThat(issuesCaptor.getValue()).hasSize(2);
            assertThat(issuesCaptor.getValue().get(0).code()).isEqualTo("DATABASE_TABLE_PENDING");
            assertThat(issuesCaptor.getValue().get(1).code()).isEqualTo("FIELD_CANDIDATE");
            verify(deps.figmaApiClient).queryImages("allowed-file", List.of("9:9"));
        } finally {
            server.stop(0);
        }
    }

    // ===== 22/23번 문서 A-01(a~e)/T-04: bindFigmaDesignRequestTable =====

    /**
     * T-04: AWAITING_TABLE_BINDING 상태에서 database/tableName을 채우면 ANALYZED로 되돌아간 뒤
     * 기존 generateBundle() 파이프라인을 재실행해 고신뢰 매칭이면(ScreenSpecification이 즉시
     * APPROVED로 생성됨) PREVIEW_READY로 전이한다.
     */
    @Test
    void bindFigmaDesignRequestTableReanalyzesAndReachesPreviewReadyOnApprovedSpecification() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest awaitingRequest = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"));
        FigmaDesignRequest boundRequest = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"), "ebt", "emp_list", null, null);
        var awaiting = operation(awaitingRequest, FigmaDesignOperationStatus.AWAITING_TABLE_BINDING, List.of());
        var reanalyzed = operation(boundRequest, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test"))
                .thenReturn(java.util.Optional.of(awaiting), java.util.Optional.of(reanalyzed));
        when(deps.operationRepository.appendTransitionWithRequest(
                "figop-test", boundRequest, FigmaDesignOperationStatus.ANALYZED, List.of(), List.of()))
                .thenReturn(reanalyzed);

        var analysisResult = new com.krdevops.springai.model.design.DesignAnalysisResult(
                "analysis-1", "hash", null, null, "figma", "deterministic-mapper", "v1",
                List.of(), null, List.of(), LocalDateTime.now());
        when(deps.designReferenceAnalysisService.analyzeFigma(
                "https://www.figma.com/design/allowed-file/reference", "1:2", null))
                .thenReturn(analysisResult);
        var spec = approvedSpecification();
        when(deps.screenSpecificationService.create("ebt", "emp_list", null, null, null))
                .thenReturn(spec);
        var bundle = mock(FigmaExportBundle.class);
        when(bundle.metadata()).thenReturn(new FigmaExportMetadata(
                LocalDateTime.now(), "figma-screen-spec-v1", 1, "1.0.0", "registry-1"));
        when(bundle.withOperationId("figop-test")).thenReturn(bundle);
        when(bundle.withOrigin(org.mockito.ArgumentMatchers.any())).thenReturn(bundle);
        when(deps.exportService.exportBundle(new FigmaScreenExportRequest(
                spec.id(), spec.version(), "emp_list_LIST", null, "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW))).thenReturn(bundle);
        when(deps.artifactService.saveFigmaExportBundle(bundle)).thenReturn(
                new DesignArtifactService.FigmaBundleArtifact(
                        "emp-list-v1-bundle", "figma-bundles/emp-list/v1",
                        "emp-list", 1, "a".repeat(64), LocalDateTime.now()));
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.PREVIEW_READY),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(boundRequest, FigmaDesignOperationStatus.PREVIEW_READY, List.of()));

        var result = deps.service.bindTable("figop-test", "ebt", "emp_list");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
        verify(deps.operationRepository).appendTransitionWithRequest(
                "figop-test", boundRequest, FigmaDesignOperationStatus.ANALYZED, List.of(), List.of());
    }

    /**
     * T-04: 매칭이 애매해 ScreenSpecification이 REVIEW_REQUIRED로 생성되면 자동 확정하지 않고
     * REJECTED로 전이하며 reviseScreenSpecification 등 수동 경로를 안내한다.
     * ({@code FigmaDesignOperationStatus}에는 REVIEW_REQUIRED 값 자체가 없다 — C-01에서 이미
     * 정정된 설계이므로 이 테스트도 REJECTED를 검증한다.)
     */
    @Test
    void bindFigmaDesignRequestTableRejectsWhenScreenSpecificationReviewRequired() throws Exception {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest awaitingRequest = FigmaDesignRequest.imageReference(
                "이미지처럼", "allowed-file", List.of("9:9"));
        FigmaDesignRequest boundRequest = FigmaDesignRequest.imageReference(
                "이미지처럼", "allowed-file", List.of("9:9"), "ebt", "emp_list", null, null);
        var awaiting = operation(awaitingRequest, FigmaDesignOperationStatus.AWAITING_TABLE_BINDING, List.of());
        var reanalyzed = operation(boundRequest, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test"))
                .thenReturn(java.util.Optional.of(awaiting), java.util.Optional.of(reanalyzed));
        when(deps.operationRepository.appendTransitionWithRequest(
                "figop-test", boundRequest, FigmaDesignOperationStatus.ANALYZED, List.of(), List.of()))
                .thenReturn(reanalyzed);

        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.png", exchange -> {
            byte[] body = {1, 2, 3, 4};
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String imageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/image.png";
            when(deps.figmaApiClient.queryImages("allowed-file", List.of("9:9"))).thenReturn(
                    new com.krdevops.springai.service.FigmaApiClient.FigmaImageUrls(
                            java.util.Map.of("9:9", imageUrl), List.of(), java.time.Instant.now()));
            var analysisResult = new com.krdevops.springai.model.design.DesignAnalysisResult(
                    "analysis-2", "hash", null, null, "vision", "gpt-4o-mini", "v1",
                    List.of(), null, List.of(), LocalDateTime.now());
            when(deps.designReferenceAnalysisService.analyze(
                    org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.isNull()))
                    .thenReturn(analysisResult);
            var reviewRequiredSpec = specificationWithStatus(ScreenSpecStatus.REVIEW_REQUIRED);
            when(deps.screenSpecificationService.create("ebt", "emp_list", null, null, null))
                    .thenReturn(reviewRequiredSpec);
            when(deps.operationRepository.appendTransition(
                    org.mockito.ArgumentMatchers.eq("figop-test"),
                    org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.anyList()))
                    .thenReturn(operation(boundRequest, FigmaDesignOperationStatus.REJECTED, List.of()));

            var result = deps.service.bindTable("figop-test", "ebt", "emp_list");

            assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        } finally {
            server.stop(0);
        }
    }

    /** A-01e 완료 게이트: AWAITING_TABLE_BINDING이 아닌 Operation에는 명확한 오류로 거부한다. */
    @Test
    void bindFigmaDesignRequestTableRejectsWhenOperationNotAwaitingTableBinding() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));

        assertThatThrownBy(() -> deps.service.bindTable("figop-test", "ebt", "emp_list"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AWAITING_TABLE_BINDING");
    }

    /** IMAGE_REFERENCE에서 Figma 이미지 export 자체가 실패하면 여전히 즉시 REJECTED다(분석 이전 단계이므로 미룰 후보가 없음). */
    @Test
    void generateBundleForImageReferenceStillRejectsWhenImageExportFailsRegardlessOfDatabase() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.imageReference(
                "이미지처럼", "allowed-file", List.of("9:9"), null, null, "직원 목록", "crud");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        when(deps.figmaApiClient.queryImages("allowed-file", List.of("9:9")))
                .thenThrow(new RuntimeException("boom"));
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
    }

    /** R6-035: queryImages()가 준 CDN URL을 실제로 내려받아 analyze()에 넘기고, 임시 파일은 정리한다. */
    @Test
    void generateBundleForImageReferenceDownloadsAndAnalyzesFirstImageNode() throws Exception {
        var deps = bundleGenerationDeps();
        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/image.png", exchange -> {
            byte[] body = {1, 2, 3, 4};
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String imageUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/image.png";
            FigmaDesignRequest request = FigmaDesignRequest.imageReference(
                    "이미지처럼", "allowed-file", List.of("9:9"), "ebt", "emp_list", "직원 목록", "crud");
            var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
            when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
            when(deps.figmaApiClient.queryImages("allowed-file", List.of("9:9"))).thenReturn(
                    new com.krdevops.springai.service.FigmaApiClient.FigmaImageUrls(
                            java.util.Map.of("9:9", imageUrl), List.of(), java.time.Instant.now()));
            var analysisResult = new com.krdevops.springai.model.design.DesignAnalysisResult(
                    "analysis-2", "hash", null, null, "vision", "gpt-4o-mini", "v1",
                    List.of(), null, List.of(), LocalDateTime.now());
            @SuppressWarnings("unchecked")
            org.mockito.ArgumentCaptor<String> pathCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            when(deps.designReferenceAnalysisService.analyze(
                    pathCaptor.capture(), org.mockito.ArgumentMatchers.isNull(),
                    org.mockito.ArgumentMatchers.eq("crud")))
                    .thenReturn(analysisResult);
            var spec = approvedSpecification();
            when(deps.screenSpecificationService.create("ebt", "emp_list", "직원 목록", "crud", null))
                    .thenReturn(spec);
            var bundle = mock(FigmaExportBundle.class);
            when(bundle.metadata()).thenReturn(new FigmaExportMetadata(
                    LocalDateTime.now(), "figma-screen-spec-v1", 1, "1.0.0", "registry-1"));
            when(bundle.withOperationId("figop-test")).thenReturn(bundle);
            when(bundle.withOrigin(org.mockito.ArgumentMatchers.any())).thenReturn(bundle);
            when(deps.exportService.exportBundle(org.mockito.ArgumentMatchers.any())).thenReturn(bundle);
            when(deps.artifactService.saveFigmaExportBundle(bundle)).thenReturn(
                    new DesignArtifactService.FigmaBundleArtifact(
                            "emp-list-v1-bundle", "figma-bundles/emp-list/v1",
                            "emp-list", 1, "a".repeat(64), LocalDateTime.now()));
            when(deps.operationRepository.appendTransition(
                    org.mockito.ArgumentMatchers.eq("figop-test"),
                    org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.PREVIEW_READY),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList(),
                    org.mockito.ArgumentMatchers.anyList()))
                    .thenReturn(operation(request, FigmaDesignOperationStatus.PREVIEW_READY, List.of()));

            var result = deps.service.generateBundle("figop-test");

            assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
            assertThat(pathCaptor.getValue()).contains("figma-image-").endsWith(".png");
            assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(pathCaptor.getValue()))).isFalse();
            verify(bundle).withOrigin(FigmaExportMetadata.Origin.ORCHESTRATED);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void generateBundleForImageReferenceRejectsWhenNodeRenderFailed() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.imageReference(
                "이미지처럼", "allowed-file", List.of("9:9"), "ebt", "emp_list", "직원 목록", "crud");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        when(deps.figmaApiClient.queryImages("allowed-file", List.of("9:9"))).thenReturn(
                new com.krdevops.springai.service.FigmaApiClient.FigmaImageUrls(
                        java.util.Map.of(), List.of("9:9"), java.time.Instant.now()));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("FIGMA_IMAGE_EXPORT_FAILED"));
    }

    @Test
    void generateBundleForComponentSpecifiedRejectsWhenComponentMissingFromRegistry() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.componentSpecified(
                "버튼과 표", "allowed-file", List.of("krds.unknown-component"),
                "ebt", "emp_list", "직원 목록", "crud");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        when(deps.designSystemQueryService.findLatestRegistry("krds")).thenReturn(
                new com.krdevops.springai.model.designsystem.ComponentRegistry(
                        "krds", "1.0.0", "registry-1", null, java.util.Map.of()));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("COMPONENT_NOT_IN_REGISTRY"));
        verify(deps.screenSpecificationService, never()).create(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(com.krdevops.springai.model.design.UiDesignSpec.class));
    }

    /**
     * T-05: COMPONENT_SPECIFIED는 REFERENCE_STYLE/IMAGE_REFERENCE와 달리 이번 절충안의 대상이
     * 아니다(S-01이 명시적으로 스코프 밖으로 남겨둔 부분) — database/tableName이 없으면
     * AWAITING_TABLE_BINDING을 거치지 않고 기존과 동일하게 즉시 REJECTED(DATABASE_TABLE_REQUIRED)된다.
     */
    @Test
    void generateBundleForComponentSpecifiedWithoutDatabaseStillRejectsInsteadOfAwaitingTableBinding() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.componentSpecified(
                "버튼과 표", "allowed-file", List.of("krds.button"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        when(deps.designSystemQueryService.findLatestRegistry("krds")).thenReturn(
                new com.krdevops.springai.model.designsystem.ComponentRegistry(
                        "krds", "1.0.0", "registry-1", null, java.util.Map.of(
                                "krds.button", new ComponentRegistryEntry(
                                        "set-key-button", "Button",
                                        ComponentRegistryEntry.PublishStatus.CURRENT,
                                        java.util.Map.of(), java.util.Map.of()))));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("DATABASE_TABLE_REQUIRED"));
    }

    /**
     * T-05: TEXT_DESCRIPTION은 이 절충안과 무관하게 여전히 자동 Bundle 생성을 지원하지 않아
     * generateBundle() 자체가 즉시 예외를 던진다(AWAITING_TABLE_BINDING을 거칠 여지 자체가 없음).
     */
    @Test
    void generateBundleForTextDescriptionThrowsUnsupported() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.textDescription("사용자 목록", "allowed-file");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));

        assertThatThrownBy(() -> deps.service.generateBundle("figop-test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT_DESCRIPTION");
    }

    /** T-07: 존재하지 않는 Operation ID로 bindFigmaDesignRequestTable을 호출하면 명확한 오류로 거부한다. */
    @Test
    void bindFigmaDesignRequestTableRejectsWhenOperationIdNotFound() {
        var deps = bundleGenerationDeps();
        when(deps.operationRepository.findLatest("figop-missing")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> deps.service.bindTable("figop-missing", "ebt", "emp_list"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("figop-missing");
    }

    @Test
    void generateBundleForModifyExistingRequiresScreenSpecificationId() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.modifyExisting(
                "버튼 색상 변경", "allowed-file", List.of("1:2"));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("SCREEN_SPECIFICATION_ID_REQUIRED"));
    }

    @Test
    void generateBundleForMultiScreenFlowRejectsAllWhenOneScreenMissingDatabase() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.multiScreenFlow("플로우", "allowed-file", List.of(
                new com.krdevops.springai.model.figma.contract.FigmaScreenRequest(
                        "screen-a", "화면 A", "ebt", "table_a"),
                new com.krdevops.springai.model.figma.contract.FigmaScreenRequest(
                        "screen-b", "화면 B", null, null)));
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        when(deps.screenSpecificationService.create("ebt", "table_a", "screen-a", null, null))
                .thenReturn(approvedSpecification());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).anySatisfy(
                issue -> assertThat(issue.code()).isEqualTo("DATABASE_TABLE_REQUIRED"));
        // 전체 거부이므로 성공한 screen-a의 Bundle도 Operation에는 반영되지 않는다(all-or-nothing).
        verify(deps.artifactService, never()).saveFigmaExportBundle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateBundleRejectsWhenOperationIsNotAnalyzed() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.referenceStyle(
                "기존 목록처럼", "allowed-file", List.of("1:2"));
        var previewReady = operation(request, FigmaDesignOperationStatus.PREVIEW_READY, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(previewReady));

        assertThatThrownBy(() -> deps.service.generateBundle("figop-test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANALYZED");
    }

    // ===== R6-038(2026-08-18): PLATFORM_CONVERT — 기존 화면명세를 다른 플랫폼으로 변환 =====

    @Test
    void generateBundleForPlatformConvertRequiresScreenSpecificationId() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.platformConvert(
                "모바일로 변환", "allowed-file", List.of("1:2"), "MOBILE");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("SCREEN_SPECIFICATION_ID_REQUIRED"));
        verify(deps.screenSpecificationService, never()).get(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void generateBundleForPlatformConvertRejectsWhenSpecificationNotApproved() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.platformConvert(
                "모바일로 변환", "allowed-file", List.of("1:2"), "MOBILE", "spec-emp-list");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        when(deps.screenSpecificationService.get("spec-emp-list"))
                .thenReturn(specificationWithStatus(ScreenSpecStatus.REVIEW_REQUIRED));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.REJECTED),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.REJECTED, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.REJECTED);
        assertThat(issuesCaptor.getValue()).singleElement()
                .satisfies(issue -> assertThat(issue.code()).isEqualTo("SCREEN_SPECIFICATION_NOT_APPROVED"));
        verify(deps.exportService, never()).exportBundle(org.mockito.ArgumentMatchers.any());
    }

    /**
     * R6-038: 승인된 화면명세를 DESKTOP export 뒤 targetPlatform으로 재구성한다. 기본 Component
     * Swap 정책({@link FigmaPlatformConversionService#defaultPolicy()})은 규칙이 비어 있으므로
     * 이 End-to-End 경로에서는 Swap이 실제로 발생하지 않는다 — Swap 자체의 트리 재작성 로직은
     * {@link #applyComponentSwapsReplacesOnlySwappedNodeWhenTargetExistsInRegistry()} 등에서
     * 직접 검증한다.
     */
    @Test
    void generateBundleForPlatformConvertExportsApprovedSpecificationAsTargetViewport() {
        var deps = bundleGenerationDeps();
        FigmaDesignRequest request = FigmaDesignRequest.platformConvert(
                "모바일로 변환", "allowed-file", List.of("1:2"), "MOBILE", "spec-emp-list");
        var analyzed = operation(request, FigmaDesignOperationStatus.ANALYZED, List.of());
        when(deps.operationRepository.findLatest("figop-test")).thenReturn(java.util.Optional.of(analyzed));
        var spec = approvedSpecification();
        when(deps.screenSpecificationService.get("spec-emp-list")).thenReturn(spec);

        FigmaNodeSpec content = new FigmaNodeSpec("root", FigmaNodeSpec.NodeType.PAGE, "PAGE", Map.of(), null,
                List.of(new FigmaNodeSpec("child-1", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT", Map.of(),
                        new ResolvedComponentRef(SemanticRole.DATA_TABLE, "krds.table", "set-key-table",
                                null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                        List.of())));
        ComponentRegistry registry = new ComponentRegistry("krds", "1.0.0", "registry-1", null,
                Map.of("krds.table", new ComponentRegistryEntry("set-key-table", Map.of())));
        FigmaScreenSpec sourceSpec = new FigmaScreenSpec(
                "spec-emp-list-screen", 1, spec.id(), spec.version(), FigmaScreenType.LIST, LayoutPattern.STANDARD,
                "직원 목록", null, "DESKTOP", "DRAFT",
                new FigmaScreenSpec.DesignSystemRef("krds", "1.0.0", "registry-1"), content, List.of());
        FigmaExportBundle sourceBundle = new FigmaExportBundle(
                sourceSpec, mock(DesignSystemProfileSnapshot.class),
                new ComponentRegistrySnapshot(registry, LocalDateTime.now()), null, null, null,
                new FigmaExportMetadata(LocalDateTime.now(), "figma-screen-spec-v1", spec.version(), "1.0.0",
                        "registry-1"));
        when(deps.exportService.exportBundle(new FigmaScreenExportRequest(
                spec.id(), spec.version(), "emp_list_LIST", null, "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW))).thenReturn(sourceBundle);
        org.mockito.ArgumentCaptor<FigmaExportBundle> bundleCaptor =
                org.mockito.ArgumentCaptor.forClass(FigmaExportBundle.class);
        when(deps.artifactService.saveFigmaExportBundle(bundleCaptor.capture())).thenReturn(
                new DesignArtifactService.FigmaBundleArtifact(
                        "emp-list-mobile-v1-bundle", "figma-bundles/emp-list-mobile/v1",
                        "emp-list-mobile", 1, "a".repeat(64), LocalDateTime.now()));
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<com.krdevops.springai.model.contract.GenerationIssue>> issuesCaptor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        when(deps.operationRepository.appendTransition(
                org.mockito.ArgumentMatchers.eq("figop-test"),
                org.mockito.ArgumentMatchers.eq(FigmaDesignOperationStatus.PREVIEW_READY),
                org.mockito.ArgumentMatchers.any(), issuesCaptor.capture(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(operation(request, FigmaDesignOperationStatus.PREVIEW_READY, List.of()));

        var result = deps.service.generateBundle("figop-test");

        assertThat(result.status()).isEqualTo(FigmaDesignOperationStatus.PREVIEW_READY);
        assertThat(issuesCaptor.getValue()).isEmpty();
        FigmaExportBundle saved = bundleCaptor.getValue();
        assertThat(saved.figmaScreenSpec().screenId()).isEqualTo("spec-emp-list-screen-mobile");
        assertThat(saved.figmaScreenSpec().viewport()).isEqualTo("MOBILE");
        assertThat(saved.metadata().origin()).isEqualTo(FigmaExportMetadata.Origin.ORCHESTRATED);
        assertThat(saved.figmaScreenSpec().content().children().get(0).componentResolution().logicalType())
                .isEqualTo("krds.table");
        verify(deps.artifactService).saveFigmaExportBundle(saved);
    }

    /** Swap 대상이 아닌 노드와 그 자식은 그대로 유지된다. */
    @Test
    void applyComponentSwapsKeepsNodeUnchangedWhenNoSwapTargetsMatch() {
        var deps = bundleGenerationDeps();
        FigmaNodeSpec node = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT", Map.of(),
                new ResolvedComponentRef(SemanticRole.DATA_TABLE, "krds.table", "set-key-table",
                        null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        ComponentRegistry registry = new ComponentRegistry("krds", "1.0.0", "registry-1", null, Map.of());

        FigmaNodeSpec result = deps.service.applyComponentSwaps(node, Map.of(), registry);

        assertThat(result.componentResolution().logicalType()).isEqualTo("krds.table");
        assertThat(result.componentResolution().componentSetKey()).isEqualTo("set-key-table");
    }

    /** componentResolution이 없는 노드(레이아웃 컨테이너 등)는 Swap 대상에서 제외된다. */
    @Test
    void applyComponentSwapsPassesThroughNodeWithoutComponentResolution() {
        var deps = bundleGenerationDeps();
        FigmaNodeSpec node = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.SECTION, "SECTION", Map.of(), null,
                List.of());
        ComponentRegistry registry = new ComponentRegistry("krds", "1.0.0", "registry-1", null, Map.of());

        FigmaNodeSpec result = deps.service.applyComponentSwaps(
                node, Map.of("krds.table", "krds.table.mobile"), registry);

        assertThat(result.componentResolution()).isNull();
    }

    /** Swap 대상 논리 타입이 결정됐지만 대체할 Registry Entry가 없으면 원본을 그대로 유지한다. */
    @Test
    void applyComponentSwapsKeepsOriginalWhenTargetLogicalTypeMissingFromRegistry() {
        var deps = bundleGenerationDeps();
        FigmaNodeSpec node = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT", Map.of(),
                new ResolvedComponentRef(SemanticRole.DATA_TABLE, "krds.table", "set-key-desktop",
                        null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        ComponentRegistry registry = new ComponentRegistry("krds", "1.0.0", "registry-1", null, Map.of());

        FigmaNodeSpec result = deps.service.applyComponentSwaps(
                node, Map.of("krds.table", "krds.table.mobile"), registry);

        assertThat(result.componentResolution().logicalType()).isEqualTo("krds.table");
        assertThat(result.componentResolution().componentSetKey()).isEqualTo("set-key-desktop");
    }

    /** 대체 대상이 Registry에 실제로 있으면 logicalType/componentSetKey를 교체하고 variantKey는 초기화한다. */
    @Test
    void applyComponentSwapsReplacesOnlySwappedNodeWhenTargetExistsInRegistry() {
        var deps = bundleGenerationDeps();
        FigmaNodeSpec swappedChild = new FigmaNodeSpec("n1", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT", Map.of(),
                new ResolvedComponentRef(SemanticRole.DATA_TABLE, "krds.table", "set-key-desktop",
                        "size=large", Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        FigmaNodeSpec untouchedChild = new FigmaNodeSpec("n2", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT",
                Map.of(),
                new ResolvedComponentRef(SemanticRole.ACTION_PRIMARY, "krds.button", "set-key-button",
                        null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        FigmaNodeSpec root = new FigmaNodeSpec("root", FigmaNodeSpec.NodeType.PAGE, "PAGE", Map.of(), null,
                List.of(swappedChild, untouchedChild));
        ComponentRegistry registry = new ComponentRegistry("krds", "1.0.0", "registry-1", null, Map.of(
                "krds.table.mobile", new ComponentRegistryEntry("set-key-mobile-table", Map.of())));

        FigmaNodeSpec result = deps.service.applyComponentSwaps(
                root, Map.of("krds.table", "krds.table.mobile"), registry);

        ResolvedComponentRef swapped = result.children().get(0).componentResolution();
        assertThat(swapped.logicalType()).isEqualTo("krds.table.mobile");
        assertThat(swapped.componentSetKey()).isEqualTo("set-key-mobile-table");
        assertThat(swapped.variantKey()).isEmpty();
        assertThat(swapped.role()).isEqualTo(SemanticRole.DATA_TABLE);
        ResolvedComponentRef untouched = result.children().get(1).componentResolution();
        assertThat(untouched.logicalType()).isEqualTo("krds.button");
        assertThat(untouched.componentSetKey()).isEqualTo("set-key-button");
    }

    /** 트리 전체에서 componentResolution이 있는 노드의 논리 타입만 중복 없이 수집한다. */
    @Test
    void collectLogicalTypesGathersDistinctLogicalTypesAcrossTree() {
        var deps = bundleGenerationDeps();
        FigmaNodeSpec grandchild = new FigmaNodeSpec("gc1", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT", Map.of(),
                new ResolvedComponentRef(SemanticRole.ACTION_PRIMARY, "krds.button", "set-key-button",
                        null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        FigmaNodeSpec childWithoutResolution = new FigmaNodeSpec(
                "c1", FigmaNodeSpec.NodeType.SECTION, "SECTION", Map.of(), null, List.of(grandchild));
        FigmaNodeSpec duplicateTypeChild = new FigmaNodeSpec("c2", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT",
                Map.of(),
                new ResolvedComponentRef(SemanticRole.DATA_TABLE, "krds.table", "set-key-table",
                        null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        FigmaNodeSpec anotherTableChild = new FigmaNodeSpec("c3", FigmaNodeSpec.NodeType.COMPONENT, "COMPONENT",
                Map.of(),
                new ResolvedComponentRef(SemanticRole.DATA_TABLE, "krds.table", "set-key-table",
                        null, Map.of(), Map.of(), "1.0.0", "1.0.0", null, null),
                List.of());
        FigmaNodeSpec root = new FigmaNodeSpec("root", FigmaNodeSpec.NodeType.PAGE, "PAGE", Map.of(), null,
                List.of(childWithoutResolution, duplicateTypeChild, anotherTableChild));

        Set<String> logicalTypes = deps.service.collectLogicalTypes(root);

        assertThat(logicalTypes).containsExactly("krds.button", "krds.table");
    }

    private com.krdevops.springai.model.design.ScreenSpecification specificationWithStatus(ScreenSpecStatus status) {
        return new com.krdevops.springai.model.design.ScreenSpecification(
                "spec-emp-list", 1, status,
                "직원 목록", "crud", "EMP_LIST",
                "ebt", "emp_list", List.of(),
                List.of(new com.krdevops.springai.model.design.PageSpec(
                        "emp_list_LIST", "EMP_LIST", List.of(), List.of())),
                List.of(), LocalDateTime.now());
    }

    private ScreenSpecificationTestDeps bundleGenerationDeps() {
        FigmaDesignOperationRepository operationRepository = mock(FigmaDesignOperationRepository.class);
        var designReferenceAnalysisService = mock(com.krdevops.springai.service.DesignReferenceAnalysisService.class);
        var screenSpecificationService = mock(com.krdevops.springai.service.ScreenSpecificationService.class);
        var designSystemQueryService = mock(com.krdevops.springai.service.designsystem.DesignSystemQueryService.class);
        var exportService = mock(FigmaScreenExportService.class);
        var artifactService = mock(DesignArtifactService.class);
        var figmaApiClient = mock(com.krdevops.springai.service.FigmaApiClient.class);
        FigmaDesignOrchestrationService service = new FigmaDesignOrchestrationService(
                mock(FigmaContextAnalyzer.class), mock(FigmaFileAllowlistValidator.class),
                exportService, artifactService, operationRepository,
                new FigmaPlatformConversionService(new com.krdevops.springai.service.designsystem.ComponentSwapPolicyResolver()),
                designReferenceAnalysisService, screenSpecificationService,
                new com.krdevops.springai.service.designsystem.ComponentRegistryResolver(),
                designSystemQueryService, figmaApiClient, new DesignFieldCandidateExtractor());
        return new ScreenSpecificationTestDeps(service, operationRepository, designReferenceAnalysisService,
                screenSpecificationService, designSystemQueryService, exportService, artifactService, figmaApiClient);
    }

    private record ScreenSpecificationTestDeps(
            FigmaDesignOrchestrationService service,
            FigmaDesignOperationRepository operationRepository,
            com.krdevops.springai.service.DesignReferenceAnalysisService designReferenceAnalysisService,
            com.krdevops.springai.service.ScreenSpecificationService screenSpecificationService,
            com.krdevops.springai.service.designsystem.DesignSystemQueryService designSystemQueryService,
            FigmaScreenExportService exportService,
            DesignArtifactService artifactService,
            com.krdevops.springai.service.FigmaApiClient figmaApiClient) {
    }

    private com.krdevops.springai.model.design.ScreenSpecification approvedSpecification() {
        return new com.krdevops.springai.model.design.ScreenSpecification(
                "spec-emp-list", 1, com.krdevops.springai.model.design.ScreenSpecStatus.APPROVED,
                "직원 목록", "crud", "EMP_LIST",
                "ebt", "emp_list", List.of(),
                List.of(new com.krdevops.springai.model.design.PageSpec(
                        "emp_list_LIST", "EMP_LIST", List.of(), List.of())),
                List.of(), LocalDateTime.now());
    }

    private FigmaDesignOrchestrationService service(
            FigmaContextAnalyzer analyzer,
            FigmaFileAllowlistValidator allowlist,
            FigmaScreenExportService exportService,
            DesignArtifactService artifactService,
            FigmaDesignOperationRepository operationRepository) {
        return new FigmaDesignOrchestrationService(
                analyzer, allowlist, exportService, artifactService, operationRepository,
                new FigmaPlatformConversionService(new com.krdevops.springai.service.designsystem.ComponentSwapPolicyResolver()),
                mock(com.krdevops.springai.service.DesignReferenceAnalysisService.class),
                mock(com.krdevops.springai.service.ScreenSpecificationService.class),
                new com.krdevops.springai.service.designsystem.ComponentRegistryResolver(),
                mock(com.krdevops.springai.service.designsystem.DesignSystemQueryService.class),
                mock(com.krdevops.springai.service.FigmaApiClient.class), new DesignFieldCandidateExtractor());
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
