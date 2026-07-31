package com.krdevops.springai.model.figma;

/** FigmaScreenExportService.export()의 입력. 어떤 화면을 어떤 디자인 시스템 기준으로 내보낼지 지정한다. */
public record FigmaScreenExportRequest(
        @jakarta.validation.constraints.NotBlank String screenSpecificationId,
        @jakarta.validation.constraints.Positive Integer screenSpecificationVersion,
        @jakarta.validation.constraints.NotBlank String pageId,
        @jakarta.validation.constraints.Size(max = 64) String designSystemProfileId,
        String viewport,
        FigmaExportMode exportMode,
        FigmaSyncMode syncMode
) {
    public FigmaScreenExportRequest {
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
