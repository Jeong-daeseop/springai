package com.krdevops.springai.model.capture;

import java.util.List;

public record RenderedDesignPackageManifest(
        String packageVersion, String mimeType, String captureId, String documentKey,
        String contentHash, List<Entry> entries,
        int nodeCount, int assetCount, int componentCount, int warningCount,
        String extractorVersion, String browserVersion) {
    public static final String PACKAGE_VERSION = "figpack-v1";
    public static final String MIME_TYPE = "application/vnd.springai.figpack+zip";
    public RenderedDesignPackageManifest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }

    /** 신규 §5.11 요약 필드(node/asset/component/warning count, extractor/browser version) 도입 전 호출자 호환용. */
    public RenderedDesignPackageManifest(String packageVersion, String mimeType, String captureId, String documentKey,
            String contentHash, List<Entry> entries) {
        this(packageVersion, mimeType, captureId, documentKey, contentHash, entries, 0, 0, 0, 0, null, null);
    }

    public record Entry(String path, long byteLength, String sha256) {}
}
