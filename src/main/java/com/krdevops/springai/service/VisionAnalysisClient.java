package com.krdevops.springai.service;

import com.krdevops.springai.model.design.UiDesignSpec;
import com.krdevops.springai.model.design.VisionAnalysisRequest;

public interface VisionAnalysisClient {
    UiDesignSpec analyze(VisionAnalysisRequest request);
    String providerId();
    String modelId();

    /**
     * R6-045: 실제로 이미지를 첨부해 API를 호출하기 전에, 설정된 모델이 이미지 입력(Vision)을
     * 지원하는지 사전 점검한다. 알려진 Vision 모델 이름 패턴과 대조하는 결정론적 판정이며
     * 원격 capability 조회는 하지 않는다({@link VisionModelCapability} 참고).
     */
    default boolean supportsVision() {
        return VisionModelCapability.supports(providerId(), modelId());
    }
}
