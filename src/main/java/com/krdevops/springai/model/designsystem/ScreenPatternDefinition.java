package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.design.role.SemanticRole;

import java.util.List;

public record ScreenPatternDefinition(
        ScreenPattern pattern,
        String version,
        List<SlotDefinition> slots
) {
    public ScreenPatternDefinition {
        if (pattern == null || version == null || version.isBlank()) {
            throw new IllegalArgumentException("Pattern과 version은 필수입니다.");
        }
        slots = slots == null ? List.of() : List.copyOf(slots);
    }

    public record SlotDefinition(
            SemanticRole role,
            int minCount,
            Integer maxCount,
            List<SemanticRole> allowedChildren,
            int order
    ) {
        public SlotDefinition {
            if (role == null || minCount < 0 || (maxCount != null && maxCount < minCount)) {
                throw new IllegalArgumentException("유효하지 않은 Slot Cardinality입니다: " + role);
            }
            allowedChildren = allowedChildren == null ? List.of() : List.copyOf(allowedChildren);
        }
    }
}
