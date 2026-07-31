package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationContext;
import com.krdevops.springai.service.generation.model.GenerationStage;

/**
 * 저장된 산출물을 검증한다. 명세서 §10.7.
 *
 * <p>명세서 §10.7은 Verifier를 별도 개념으로 부르지만 §10.1의 Stage enum에는 {@code PRE_VERIFY}와
 * {@code VERIFY}가 따로 있다 — 그래서 Verifier도 Processor처럼 stage/order를 갖고
 * {@link GenerationVerifierRunner}가 그 순서대로 실행한다. 이렇게 해야
 * "Directory 검증이 항상 Common Contract 감사보다 먼저"라는 실제 순서가 배선이 아니라
 * 타입 수준에서 보장된다.
 */
public interface GenerationVerifier {

    String id();

    GenerationStage stage();

    int order();

    default boolean supports(GenerationContext context) {
        return true;
    }

    VerificationResult verify(GenerationProcessingContext context);
}
