package com.krdevops.springai.model.contract;

import java.util.List;

/**
 * I-0: Desktop/Tablet/Mobile 플랫폼별 레이아웃 정책.
 * Figma와 Thymeleaf 두 흐름이 공유하는 Responsive 규칙.
 */
public record PlatformLayoutPolicy(
        String policyVersion,
        List<ViewportPolicy> viewports,
        List<ComponentSwapRule> componentSwaps,
        String canonicalHash
) {
    public record ViewportPolicy(
            String platform,
            int viewportWidth,
            int gridColumns,
            String navigationStyle,
            List<String> visibleComponents
    ) {
    }

    public record ComponentSwapRule(
            String fromComponent,
            String toComponent,
            String platform,
            String reason
    ) {
    }

    public ViewportPolicy getViewportFor(String platform) {
        return viewports.stream()
                .filter(v -> v.platform.equals(platform))
                .findFirst()
                .orElse(null);
    }
}
