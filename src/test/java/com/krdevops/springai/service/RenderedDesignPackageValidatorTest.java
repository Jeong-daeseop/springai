package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedDesignPackageManifest;
import com.krdevops.springai.model.capture.RenderedNode;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderedDesignPackageValidatorTest {

    @Test
    void validatesManifestEntryHashesReferencesAndCanonicalContentHash() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        RenderedDesignDocumentValidator documentValidator = new RenderedDesignDocumentValidator(mapper);
        String captureId = "11111111-1111-4111-8111-111111111111";
        String documentKey = "b".repeat(64);
        RenderedNode root = new RenderedNode("root", null, "ELEMENT", "html", "", null,
                null, null, true, new RenderedNode.Bounds(0, 0, 1440, 1200), Map.of(), List.of());
        RenderedDesignDocument withoutHash = new RenderedDesignDocument(
                RenderedDesignDocument.SCHEMA_VERSION, captureId, documentKey, "a".repeat(64),
                new RenderedDesignDocument.Source("RENDERED_WEB_PAGE", "JSP", "http://localhost/",
                        "http://localhost/", "c".repeat(64), "2026-01-01T00:00:00Z"),
                new RenderedDesignDocument.Environment("desktop", 1440, 1200, 1, "ko-KR",
                        "Asia/Seoul", "light", true, "chromium"),
                new RenderedDesignDocument.Page("목록", "root", 1440, 1200, 0, 0, "white"),
                List.of(root), List.of(), Map.of(), List.of(), List.of(), List.of(), Map.of());
        String hash = documentValidator.calculateContentHash(withoutHash);
        RenderedDesignDocument document = new RenderedDesignDocument(withoutHash.schemaVersion(), captureId,
                documentKey, hash, withoutHash.source(), withoutHash.environment(), withoutHash.page(),
                withoutHash.nodes(), withoutHash.assets(), withoutHash.tokens(),
                withoutHash.componentCandidates(), withoutHash.interactions(), withoutHash.warnings(),
                withoutHash.extractor());
        byte[] documentBytes = mapper.writeValueAsBytes(document);
        var entry = new RenderedDesignPackageManifest.Entry("document.json", documentBytes.length,
                sha256(documentBytes));
        var manifest = new RenderedDesignPackageManifest(RenderedDesignPackageManifest.PACKAGE_VERSION,
                RenderedDesignPackageManifest.MIME_TYPE, captureId, documentKey, hash, List.of(entry));
        byte[] pack = zip(Map.of("manifest.json", mapper.writeValueAsBytes(manifest),
                "document.json", documentBytes));
        var validator = new RenderedDesignPackageValidator(mapper, new WebCaptureProperties(), documentValidator);

        var validated = validator.validate(pack, captureId, documentKey);

        assertThat(validated.document().contentHash()).isEqualTo(hash);
    }

    @Test
    void rejectsZipEntryPathTraversalBeforeManifestParsing() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var validator = new RenderedDesignPackageValidator(mapper, new WebCaptureProperties(),
                new RenderedDesignDocumentValidator(mapper));
        byte[] pack = zip(Map.of("../escape.json", "blocked".getBytes()));

        assertThatThrownBy(() -> validator.validate(pack,
                "11111111-1111-4111-8111-111111111111", "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("안전하지 않은");
    }

    @Test
    void rejectsManifestHashTampering() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String captureId = "11111111-1111-4111-8111-111111111111";
        String documentKey = "a".repeat(64);
        byte[] document = "{}".getBytes();
        var manifest = new RenderedDesignPackageManifest(RenderedDesignPackageManifest.PACKAGE_VERSION,
                RenderedDesignPackageManifest.MIME_TYPE, captureId, documentKey, "b".repeat(64),
                List.of(new RenderedDesignPackageManifest.Entry("document.json", document.length,
                        "0".repeat(64))));
        byte[] pack = zip(Map.of("manifest.json", mapper.writeValueAsBytes(manifest),
                "document.json", document));
        var validator = new RenderedDesignPackageValidator(mapper, new WebCaptureProperties(),
                new RenderedDesignDocumentValidator(mapper));

        assertThatThrownBy(() -> validator.validate(pack, captureId, documentKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash가 일치하지 않습니다");
    }

    @Test
    void rejectsZipBombByTotalUncompressedSize() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setMaxUncompressedArtifactMb(1);
        byte[] pack = zip(Map.of("oversized.bin", new byte[1024 * 1024 + 1]));
        var validator = new RenderedDesignPackageValidator(mapper, properties,
                new RenderedDesignDocumentValidator(mapper));

        assertThatThrownBy(() -> validator.validate(pack,
                "11111111-1111-4111-8111-111111111111", "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("압축 해제 크기 제한");
    }

    @Test
    void rejectsCaseInsensitiveEntryCollision() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("assets/Logo.png", new byte[]{1});
        entries.put("assets/logo.png", new byte[]{2});
        byte[] pack = zip(entries);
        var validator = new RenderedDesignPackageValidator(mapper, new WebCaptureProperties(),
                new RenderedDesignDocumentValidator(mapper));

        assertThatThrownBy(() -> validator.validate(pack,
                "11111111-1111-4111-8111-111111111111", "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복 제한");
    }

    @Test
    void rejectsMoreThanMaximumEntryCount() throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        byte[] pack = zipEntries(5_101);
        var validator = new RenderedDesignPackageValidator(mapper, new WebCaptureProperties(),
                new RenderedDesignDocumentValidator(mapper));

        assertThatThrownBy(() -> validator.validate(pack,
                "11111111-1111-4111-8111-111111111111", "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entry 수 또는 중복 제한");
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] zipEntries(int count) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (int index = 0; index < count; index++) {
                zip.putNextEntry(new ZipEntry("assets/entry-" + index));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
