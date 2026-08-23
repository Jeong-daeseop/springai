package com.krdevops.springai.model.renderer;

import java.util.Set;

/** 한 생성 요청이 Renderer에 요구하는 기능과 실제로 선택하려는 fallback. */
public record RendererCapabilityRequirement(
        Set<RendererFeature> requiredFeatures,
        Set<RendererFallback> attemptedFallbacks
) {
    public RendererCapabilityRequirement {
        requiredFeatures = requiredFeatures == null ? Set.of() : Set.copyOf(requiredFeatures);
        attemptedFallbacks = attemptedFallbacks == null ? Set.of() : Set.copyOf(attemptedFallbacks);
    }

    public static RendererCapabilityRequirement features(RendererFeature... features) {
        return new RendererCapabilityRequirement(Set.of(features), Set.of());
    }
}
