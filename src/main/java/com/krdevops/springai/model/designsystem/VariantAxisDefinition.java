package com.krdevops.springai.model.designsystem;

import java.util.Set;

/** 논리 Variant Axis와 실제 Figma Component Property의 공개 계약. */
public record VariantAxisDefinition(
        String logicalName,
        String figmaProperty,
        Set<String> allowedValues,
        boolean required
) {
    public VariantAxisDefinition {
        if (logicalName == null || logicalName.isBlank()) {
            throw new IllegalArgumentException("Variant logicalName은 필수입니다.");
        }
        if (figmaProperty == null || figmaProperty.isBlank()) {
            throw new IllegalArgumentException("Variant figmaProperty는 필수입니다.");
        }
        allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
        if (required && allowedValues.isEmpty()) {
            throw new IllegalArgumentException("필수 Variant Axis에는 허용 값이 필요합니다: " + logicalName);
        }
    }
}
