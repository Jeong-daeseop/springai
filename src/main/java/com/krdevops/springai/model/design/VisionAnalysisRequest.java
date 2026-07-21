package com.krdevops.springai.model.design;

import java.util.List;

public record VisionAnalysisRequest(
        String featureType,
        List<VisionImage> images,
        String promptVersion
) {
    public VisionAnalysisRequest {
        images = images == null ? List.of() : List.copyOf(images);
    }

    public record VisionImage(int pageNumber, String mimeType, byte[] content) {}
}
