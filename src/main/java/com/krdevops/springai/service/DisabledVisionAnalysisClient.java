package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.VisionAnalysisRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.design-vision", name = "provider", havingValue = "disabled", matchIfMissing = true)
public class DisabledVisionAnalysisClient implements VisionAnalysisClient {

    @Override
    public UiDesignSpec analyze(VisionAnalysisRequest request) {
        throw new IllegalStateException(
                "디자인 비전 분석이 비활성화되어 있습니다. 보안 검토 후 app.design-vision.provider를 설정하세요.");
    }

    @Override
    public String providerId() {
        return "disabled";
    }

    @Override
    public String modelId() {
        return "none";
    }
}
