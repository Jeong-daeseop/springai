package com.krdevops.springai.service.generation.pipeline;

import com.krdevops.springai.service.generation.model.GenerationBlueprint;
import com.krdevops.springai.service.generation.model.RenderedGenerationPlan;

/**
 * Blueprint의 파일 계획을 실제 Source 문자열로 렌더링한다. 명세서 §10.4.
 *
 * <p>레이어 하나의 렌더링이 실패해도 나머지 레이어는 계속 시도하며, 실패한 레이어는
 * {@code RenderedFilePlan.renderFailure}로 표시한 채 계획에 남긴다.
 */
public interface GenerationRenderer {

    RenderedGenerationPlan render(GenerationBlueprint blueprint);
}
