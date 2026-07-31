package com.krdevops.springai.model.figma.ops;

import java.util.List;

/** 특정 DesignSystem Profile/Registry 버전을 참조하는 최신 화면 목록. */
public record DesignSystemImpact(
        String profileId,
        String profileVersion,
        String registryVersion,
        int affectedScreenCount,
        List<AffectedScreen> screens
) {
    public DesignSystemImpact {
        screens = screens == null ? List.of() : List.copyOf(screens);
    }

    public record AffectedScreen(
            String screenId,
            int screenVersion,
            String screenSpecificationId,
            int screenSpecificationVersion,
            String screenType,
            String name
    ) {
    }
}
