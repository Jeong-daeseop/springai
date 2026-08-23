package com.krdevops.springai.model.designsystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 승인된 디자인-코드 Mapping을 Renderer가 소비할 수 있도록 해석한 불변 입력 계약. */
public record DesignComponentRenderInput(
        String mappingId,
        String mappingVersion,
        String logicalType,
        String figmaComponentSetKey,
        String thymeleafFragment,
        String rendererProfile,
        Map<String, Object> fragmentParameters,
        Map<String, Object> fragmentRegions,
        String sourceRevision,
        String contentHash
) {
    public DesignComponentRenderInput {
        mappingId = requireText(mappingId, "mappingId");
        mappingVersion = requireText(mappingVersion, "mappingVersion");
        logicalType = requireText(logicalType, "logicalType");
        figmaComponentSetKey = requireText(figmaComponentSetKey, "figmaComponentSetKey");
        thymeleafFragment = requireText(thymeleafFragment, "thymeleafFragment");
        rendererProfile = requireText(rendererProfile, "rendererProfile");
        fragmentParameters = immutableOrdered(fragmentParameters);
        fragmentRegions = immutableOrdered(fragmentRegions);
        sourceRevision = requireText(sourceRevision, "sourceRevision");
        contentHash = requireText(contentHash, "contentHash");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "는 필수입니다.");
        }
        return value.trim();
    }

    private static Map<String, Object> immutableOrdered(Map<String, Object> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values == null ? Map.of() : values));
    }
}
