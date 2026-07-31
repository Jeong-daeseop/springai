package com.krdevops.springai.model.figma.hybrid;

import com.krdevops.springai.model.design.ScreenSpecification;
import java.time.LocalDateTime;
import java.util.List;

/** 사람이 Preview·수정·승인해야 하는 Hybrid 후보. */
public record FigmaHybridCandidate(
        String artifactId,
        HybridReferenceSnapshot reference,
        ScreenSpecification screenSpecification,
        List<HybridDecisionField> decisions,
        HybridConversionReport report,
        boolean humanApprovalRequired,
        LocalDateTime createdAt
) {
    public FigmaHybridCandidate {
        decisions = decisions == null ? List.of() : List.copyOf(decisions);
        humanApprovalRequired = true;
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
    }
}
