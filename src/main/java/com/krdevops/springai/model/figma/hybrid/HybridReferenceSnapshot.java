package com.krdevops.springai.model.figma.hybrid;

import java.util.List;

/** .figpack Reference와 원본 document.json의 추적 정보. 민감한 실제 텍스트·스타일 값은 복제하지 않는다. */
public record HybridReferenceSnapshot(
        String artifactId,
        String artifactRole,
        String figpackFile,
        String documentFile,
        String previewFile,
        String documentKey,
        String contentHash,
        String documentSchemaVersion,
        String requestedUrl,
        String finalUrl,
        String capturedAt,
        String viewport,
        int viewportWidth,
        int viewportHeight,
        List<String> textSourceNodeIds,
        List<String> styleSourceNodeIds,
        List<String> tokenNames
) {
    public HybridReferenceSnapshot {
        artifactRole = artifactRole == null ? "REFERENCE_SNAPSHOT" : artifactRole;
        textSourceNodeIds = textSourceNodeIds == null ? List.of() : List.copyOf(textSourceNodeIds);
        styleSourceNodeIds = styleSourceNodeIds == null ? List.of() : List.copyOf(styleSourceNodeIds);
        tokenNames = tokenNames == null ? List.of() : List.copyOf(tokenNames);
    }
}
