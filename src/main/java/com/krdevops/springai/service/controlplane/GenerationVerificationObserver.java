package com.krdevops.springai.service.controlplane;

import com.krdevops.springai.service.generation.pipeline.GenerationProcessingContext;
import com.krdevops.springai.service.generation.pipeline.GenerationVerifierRunner;

/** 검증 실행 순서를 바꾸지 않고 결과만 병행 기록하는 관찰 Port. */
public interface GenerationVerificationObserver {
    void onCompleted(GenerationProcessingContext context,
                     GenerationVerifierRunner.VerificationRunResult result);
}
