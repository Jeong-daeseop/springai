package com.krdevops.springai.model.designsystem;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Component Mapping Preview를 실제 렌더하는 데 필요한 비운영 Fixture 입력 계약. */
public record ComponentFixtureModel(
        String schemaVersion,
        Map<String, Object> figmaProperties,
        Map<String, Object> figmaSlots,
        Map<String, Object> contextVariables
) {
    public static final String SCHEMA_VERSION = "1.0";

    public ComponentFixtureModel {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("ComponentFixtureModel schemaVersion은 1.0이어야 합니다.");
        }
        figmaProperties = immutable(figmaProperties);
        figmaSlots = immutable(figmaSlots);
        contextVariables = immutable(contextVariables);
    }

    private static Map<String, Object> immutable(Map<String, Object> source) {
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        (source == null ? Map.<String, Object>of() : source).forEach((key, value) -> {
            if (key == null || key.isBlank() || value == null) {
                throw new IllegalArgumentException("Fixture 항목은 문자열 Key와 null이 아닌 값이 필요합니다.");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
