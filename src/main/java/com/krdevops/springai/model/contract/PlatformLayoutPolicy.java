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
            int gapPx,
            int paddingPx,
            String navigationStyle,
            List<String> visibleComponents
    ) {
        /** 기존 5-필드 정책 호출자 호환. 명시되지 않은 간격은 0으로 둔다. */
        public ViewportPolicy(
                String platform,
                int viewportWidth,
                int gridColumns,
                String navigationStyle,
                List<String> visibleComponents
        ) {
            this(platform, viewportWidth, gridColumns, 0, 0, navigationStyle, visibleComponents);
        }

        public ViewportPolicy {
            if (platform == null || platform.isBlank()) {
                throw new IllegalArgumentException("platform은 필수입니다.");
            }
            if (viewportWidth <= 0 || gridColumns <= 0 || gapPx < 0 || paddingPx < 0) {
                throw new IllegalArgumentException("viewport/grid/gap/padding 값이 올바르지 않습니다.");
            }
            visibleComponents = visibleComponents == null ? List.of() : List.copyOf(visibleComponents);
        }
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
