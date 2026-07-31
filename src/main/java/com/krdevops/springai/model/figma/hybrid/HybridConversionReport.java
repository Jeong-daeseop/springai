package com.krdevops.springai.model.figma.hybrid;

import java.time.LocalDateTime;
import java.util.List;

/** Reference 원본과 의미 후보/생성 결과 사이의 정량 비교 보고서. */
public record HybridConversionReport(
        String artifactId,
        String referenceContentHash,
        int sourceNodeCount,
        int visibleTextNodeCount,
        int sourceComponentCandidateCount,
        int inferredFieldCount,
        int semanticPageCount,
        int semanticFieldCount,
        int semanticActionCount,
        List<String> unmappedFieldHints,
        List<String> warnings,
        String referenceViewport,
        String semanticViewport,
        boolean viewportMatched,
        String semanticScreenId,
        Integer semanticScreenVersion,
        LocalDateTime generatedAt
) {
    public HybridConversionReport {
        unmappedFieldHints = unmappedFieldHints == null ? List.of() : List.copyOf(unmappedFieldHints);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
    }

    public HybridConversionReport withSemanticOutput(String screenId, int screenVersion, String viewport) {
        return new HybridConversionReport(
                artifactId, referenceContentHash, sourceNodeCount, visibleTextNodeCount,
                sourceComponentCandidateCount, inferredFieldCount, semanticPageCount,
                semanticFieldCount, semanticActionCount, unmappedFieldHints, warnings,
                referenceViewport, viewport, referenceViewport == null
                        || referenceViewport.equalsIgnoreCase(viewport),
                screenId, screenVersion, LocalDateTime.now());
    }
}
