package com.krdevops.springai.model.figma.hybrid;

import com.krdevops.springai.model.capture.FigmaImportArtifact;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import java.time.LocalDateTime;

/**
 * 동일 artifactId에 연결된 Reference·Semantic 최종 결과.
 * semanticResult는 FigmaExportBundle 전체를 담아 metadata().origin()이 HYBRID로 태깅된 채 보존된다.
 */
public record FigmaHybridExportResult(
        String artifactId,
        FigmaImportArtifact referenceArtifact,
        ScreenSpecification approvedScreenSpecification,
        FigmaExportBundle semanticResult,
        HybridConversionReport report,
        LocalDateTime generatedAt
) {
    public FigmaHybridExportResult {
        generatedAt = generatedAt == null ? LocalDateTime.now() : generatedAt;
    }
}
