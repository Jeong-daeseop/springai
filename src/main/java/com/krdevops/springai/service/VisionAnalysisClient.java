package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.VisionAnalysisRequest;

public interface VisionAnalysisClient {
    UiDesignSpec analyze(VisionAnalysisRequest request);
    String providerId();
    String modelId();
}
