package com.krdevops.springai.model.figma.hybrid;

import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaSyncMode;

/** Preview 검토 후 사람이 명시적으로 승인할 때 사용하는 입력. */
public record FigmaHybridApprovalRequest(
        String screenSpecificationId,
        String pageId,
        String designSystemProfileId,
        String viewport,
        FigmaExportMode exportMode,
        FigmaSyncMode syncMode,
        boolean humanApproved
) {
    public FigmaHybridApprovalRequest {
        if (!humanApproved) {
            throw new IllegalArgumentException("사람의 Preview 승인(humanApproved=true)이 필요합니다.");
        }
        if (screenSpecificationId == null || screenSpecificationId.isBlank()) {
            throw new IllegalArgumentException("screenSpecificationId는 필수입니다.");
        }
        if (pageId == null || pageId.isBlank()) {
            throw new IllegalArgumentException("pageId는 필수입니다.");
        }
        exportMode = exportMode == null ? FigmaExportMode.PREVIEW : exportMode;
        syncMode = syncMode == null ? FigmaSyncMode.PREVIEW : syncMode;
    }
}
