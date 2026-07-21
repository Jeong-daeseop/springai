package com.krdevops.springai.model.capture;

import java.util.List;

public record RenderedDesignPackageManifest(
        String packageVersion, String mimeType, String captureId, String documentKey,
        String contentHash, List<Entry> entries) {
    public static final String PACKAGE_VERSION = "figpack-v1";
    public static final String MIME_TYPE = "application/vnd.springai.figpack+zip";
    public RenderedDesignPackageManifest {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
    public record Entry(String path, long byteLength, String sha256) {}
}
