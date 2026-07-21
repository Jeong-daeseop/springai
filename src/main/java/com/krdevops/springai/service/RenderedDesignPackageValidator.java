package com.krdevops.springai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.model.capture.RenderedDesignDocument;
import com.krdevops.springai.model.capture.RenderedDesignPackageManifest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class RenderedDesignPackageValidator {
    private static final int MAX_ENTRIES = 5_100;
    private final ObjectMapper objectMapper;
    private final WebCaptureProperties properties;
    private final RenderedDesignDocumentValidator documentValidator;

    public RenderedDesignPackageValidator(ObjectMapper objectMapper, WebCaptureProperties properties,
                                          RenderedDesignDocumentValidator documentValidator) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.documentValidator = documentValidator;
    }

    public ValidatedPackage validate(byte[] packageBytes, String captureId, String documentKey) {
        if (packageBytes == null || packageBytes.length == 0
                || packageBytes.length > mb(properties.getMaxResponseMb())) {
            throw new IllegalArgumentException("figpack 압축 크기 제한을 초과했습니다.");
        }
        Map<String, byte[]> entries = unzip(packageBytes);
        try {
            RenderedDesignPackageManifest manifest = objectMapper.readValue(required(entries, "manifest.json"),
                    RenderedDesignPackageManifest.class);
            if (!RenderedDesignPackageManifest.PACKAGE_VERSION.equals(manifest.packageVersion())
                    || !RenderedDesignPackageManifest.MIME_TYPE.equals(manifest.mimeType())) {
                throw new IllegalArgumentException("지원하지 않는 figpack 계약입니다.");
            }
            if (!captureId.equals(manifest.captureId()) || !documentKey.equals(manifest.documentKey())) {
                throw new IllegalArgumentException("figpack 식별자가 요청과 일치하지 않습니다.");
            }
            Set<String> declared = new HashSet<>();
            for (RenderedDesignPackageManifest.Entry expected : manifest.entries()) {
                if (!declared.add(expected.path())) throw new IllegalArgumentException("manifest entry가 중복됩니다.");
                byte[] actual = required(entries, expected.path());
                if (actual.length != expected.byteLength() || !sha256(actual).equals(expected.sha256())) {
                    throw new IllegalArgumentException("figpack entry hash가 일치하지 않습니다: " + expected.path());
                }
            }
            if (!declared.equals(entries.keySet().stream().filter(name -> !"manifest.json".equals(name))
                    .collect(java.util.stream.Collectors.toSet()))) {
                throw new IllegalArgumentException("manifest에 선언되지 않은 figpack entry가 있습니다.");
            }
            var documentTree = objectMapper.readTree(required(entries, "document.json"));
            if (documentTree.path("source").has("contentHash")) {
                throw new IllegalArgumentException("contentHash는 document 최상위에만 허용합니다.");
            }
            RenderedDesignDocument document = objectMapper.treeToValue(documentTree,
                    RenderedDesignDocument.class);
            documentValidator.validate(document, captureId, documentKey);
            for (var asset : document.assets()) {
                byte[] bytes = required(entries, asset.path());
                if (bytes.length != asset.byteLength() || !sha256(bytes).equals(asset.contentHash())) {
                    throw new IllegalArgumentException("document asset 메타데이터가 package와 일치하지 않습니다.");
                }
            }
            if (!manifest.contentHash().equals(document.contentHash())) {
                throw new IllegalArgumentException("manifest와 document의 contentHash가 일치하지 않습니다.");
            }
            return new ValidatedPackage(packageBytes.clone(), manifest, document, Map.copyOf(entries));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("figpack을 해석할 수 없습니다.", e);
        }
    }

    private Map<String, byte[]> unzip(byte[] bytes) {
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            Map<String, byte[]> entries = new HashMap<>();
            Set<String> caseInsensitiveNames = new HashSet<>();
            long total = 0;
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory()) continue;
                validatePath(name);
                if (entries.size() >= MAX_ENTRIES || entries.containsKey(name)
                        || !caseInsensitiveNames.add(name.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("figpack entry 수 또는 중복 제한 위반입니다.");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > mb(properties.getMaxUncompressedArtifactMb())) {
                        throw new IllegalArgumentException("figpack 압축 해제 크기 제한을 초과했습니다.");
                    }
                    output.write(buffer, 0, read);
                }
                entries.put(name, output.toByteArray());
            }
            return entries;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("figpack ZIP이 올바르지 않습니다.", e);
        }
    }

    private static void validatePath(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\")
                || name.contains("..") || name.contains("\\") || name.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("안전하지 않은 figpack entry 경로입니다.");
        }
    }

    private static byte[] required(Map<String, byte[]> entries, String name) {
        byte[] value = entries.get(name);
        if (value == null) throw new IllegalArgumentException("필수 figpack entry가 없습니다: " + name);
        return value;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static long mb(int value) { return value * 1024L * 1024L; }

    public record ValidatedPackage(byte[] packageBytes, RenderedDesignPackageManifest manifest,
                                   RenderedDesignDocument document, Map<String, byte[]> entries) {
        public ValidatedPackage { packageBytes = packageBytes.clone(); }
        @Override public byte[] packageBytes() { return packageBytes.clone(); }
    }
}
