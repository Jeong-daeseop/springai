package com.krdevops.springai.model.contract;

import com.krdevops.springai.model.figma.FigmaScreenType;

import java.util.Map;

/**
 * R1-015: LIST/FORM/DETAIL/DASHBOARD 화면 유형별 기본 Layout 정책.
 * Platform(Desktop/Tablet/Mobile) 변형과 Component Swap은 {@link PlatformLayoutPolicy}가 담당한다.
 */
public record DefaultLayoutPolicy(
        String policyVersion,
        Map<FigmaScreenType, ScreenLayoutSpec> layoutByScreenType,
        String canonicalHash
) {
    public DefaultLayoutPolicy {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("policyVersion은 필수입니다.");
        }
        layoutByScreenType = layoutByScreenType == null ? Map.of() : Map.copyOf(layoutByScreenType);
        for (FigmaScreenType type : FigmaScreenType.values()) {
            if (!layoutByScreenType.containsKey(type)) {
                throw new IllegalArgumentException("layoutByScreenType에 " + type + " 정의가 없습니다.");
            }
        }
    }

    public ScreenLayoutSpec layoutFor(FigmaScreenType screenType) {
        return layoutByScreenType.get(screenType);
    }

    public record ScreenLayoutSpec(
            int gridColumns,
            int gapPx,
            int paddingPx,
            String density
    ) {
        public ScreenLayoutSpec {
            if (gridColumns <= 0) {
                throw new IllegalArgumentException("gridColumns는 1 이상이어야 합니다.");
            }
            if (gapPx < 0) {
                throw new IllegalArgumentException("gapPx는 0 이상이어야 합니다.");
            }
            if (paddingPx < 0) {
                throw new IllegalArgumentException("paddingPx는 0 이상이어야 합니다.");
            }
        }
    }
}
