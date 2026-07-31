package com.krdevops.springai.model.figma.hybrid;

import com.krdevops.springai.model.capture.FigmaImportArtifact;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaExportResult;
import java.time.LocalDateTime;

/** 동일 artifactId에 연결된 Reference·Semantic 최종 결과. */
public record FigmaHybridExportResult(
        String artifactId,
        FigmaImportArtifact referenceArtifact,
        ScreenSpecification approvedScreenSpecification,
        FigmaExportResult semanticResult,
        HybridConversionReport report,
        LocalDateTime generatedAt
) {
    public FigmaHybridExportResult {
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
    }
}
