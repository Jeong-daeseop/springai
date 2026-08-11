package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.mapper.DesignAnalysisRepository;
import com.krdevops.springai.model.capture.*;
import com.krdevops.springai.model.design.DesignAnalysisSaveOutcome;
import com.krdevops.springai.model.design.DesignSourceType;
import com.krdevops.springai.policy.WebCaptureProjectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebCaptureRelease1FlowTest {
    @TempDir Path tempDir;

    @Test
    void captureArtifactAndAnalysisFlowKeepsPiiOutOfUiSpec() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = properties();
        RenderedDesignDocumentValidator documentValidator = new RenderedDesignDocumentValidator(mapper);
        RenderedDesignPackageValidator packageValidator = new RenderedDesignPackageValidator(mapper, properties, documentValidator);
        DesignArtifactService artifacts = new DesignArtifactService(properties, mapper);
        WebCaptureClient client = mock(WebCaptureClient.class);
        when(client.capture(any(), any(URI.class), anyString(), anyString())).thenAnswer(invocation ->
                pack(mapper, documentValidator, invocation.getArgument(2), invocation.getArgument(3)));
        WebCaptureOrchestrationService orchestration = new WebCaptureOrchestrationService(properties,
                new WebCaptureUrlValidator(properties), client, packageValidator, artifacts);

        CaptureArtifactSummary captured = orchestration.capture(new CaptureWebPageRequest(
                "http://localhost:8080/list.do?token=secret", CaptureProfile.LOCAL_WEB,
                ViewportSpec.desktop(), null, "crud"));

        DesignAnalysisRepository repository = mock(DesignAnalysisRepository.class);
        when(repository.findExact(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(repository.saveOrGet(any())).thenAnswer(invocation ->
                new DesignAnalysisSaveOutcome(invocation.getArgument(0), true));
        WebCaptureAnalysisService analysisService = new WebCaptureAnalysisService(artifacts,
                new WebCaptureProjectionPolicy(), new RenderedDesignSpecMapper(),
                new WebCaptureCacheKeyFactory(), repository, properties);

        var analysis = analysisService.analyze(captured.artifactId(), "crud");

        assertThat(analysis.sourceMetadata().sourceType()).isEqualTo(DesignSourceType.WEB_CAPTURE);
        assertThat(analysis.uiSpec().toString()).contains("제목", "SAVE")
                .doesNotContain("절대 노출 금지", "secret-value", "PASSWORD_HASH");
        verify(repository).saveOrGet(any());
    }

    private WebCaptureProperties properties() {
        WebCaptureProperties value = new WebCaptureProperties();
        value.setEnabled(true); value.setExtractorApiKey("extractor-secret");
        value.setDocumentKeySecret("document-secret"); value.setArtifactBasePath(tempDir);
        value.setAllowedOrigins(List.of("http://localhost:8080"));
        return value;
    }

    private byte[] pack(ObjectMapper mapper, RenderedDesignDocumentValidator validator,
                        String captureId, String documentKey) throws Exception {
        var bounds = new RenderedNode.Bounds(0, 0, 100, 30);
        List<RenderedNode> nodes = List.of(
                new RenderedNode("root", null, "ELEMENT", "html", "", null, null, null,
                        true, bounds, Map.of(), List.of("title", "password", "profile", "save")),
                new RenderedNode("title", "root", "ELEMENT", "input", "textbox", "제목", null,
                        null, true, bounds, Map.of(), List.of()),
                new RenderedNode("password", "root", "ELEMENT", "input", "textbox", "PASSWORD_HASH", null,
                        null, true, bounds, Map.of(), List.of()),
                new RenderedNode("profile", "root", "TEXT", null, null, null, "절대 노출 금지 user@example.com", null,
                        true, bounds, Map.of(), List.of()),
                new RenderedNode("save", "root", "BUTTON", "button", "button", null, "저장", null,
                        true, bounds, Map.of(), List.of()));
        RenderedDesignDocument base = new RenderedDesignDocument(RenderedDesignDocument.SCHEMA_VERSION,
                captureId, documentKey, "a".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "UNKNOWN",
                        "http://localhost:8080/list.do?token=***", "http://localhost:8080/list.do?token=***",
                        "c".repeat(64), "2026-07-21T00:00:00Z"),
                new RenderedDesignDocument.Environment("desktop", 1440, 1200, 1, "ko-KR",
                        "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page("목록", "root", 1440, 1200, 0, 0, "white"),
                nodes, List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of("name", "test"));
        String hash = validator.calculateContentHash(base);
        RenderedDesignDocument document = new RenderedDesignDocument(base.schemaVersion(), captureId,
                documentKey, hash, base.source(), base.environment(), base.page(), base.nodes(), base.assets(),
                base.tokens(), base.componentCandidates(), base.interactions(), base.warnings(), base.extractor());
        byte[] documentBytes = mapper.writeValueAsBytes(document);
        byte[] preview = new byte[]{1, 2, 3};
        var manifest = new RenderedDesignPackageManifest(RenderedDesignPackageManifest.PACKAGE_VERSION,
                RenderedDesignPackageManifest.MIME_TYPE, captureId, documentKey, hash, List.of(
                new RenderedDesignPackageManifest.Entry("document.json", documentBytes.length, sha(documentBytes)),
                new RenderedDesignPackageManifest.Entry("preview.png", preview.length, sha(preview))));
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("manifest.json", mapper.writeValueAsBytes(manifest));
        entries.put("document.json", documentBytes); entries.put("preview.png", preview);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) { zip.putNextEntry(new ZipEntry(entry.getKey())); zip.write(entry.getValue()); zip.closeEntry(); }
        }
        return output.toByteArray();
    }

    private static String sha(byte[] value) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
