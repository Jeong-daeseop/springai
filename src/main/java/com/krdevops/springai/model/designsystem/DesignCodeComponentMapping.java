package com.krdevops.springai.model.designsystem;

import com.krdevops.springai.model.artifact.ContentHashes;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Figma Component Set의 시각 계약을 Thymeleaf Fragment 호출 계약에 연결하는 버전 산출물. */
public record DesignCodeComponentMapping(
        String mappingId,
        String version,
        Status status,
        String contentHash,
        String logicalType,
        String figmaComponentSetKey,
        String thymeleafFragment,
        List<PropertyMapping> propertyMappings,
        List<SlotMapping> slotMappings,
        @Nullable Map<String, Object> fixtureModel,
        List<String> supportedRendererProfiles,
        String sourceRevision,
        @Nullable String approvedBy,
        @Nullable Instant approvedAt
) {
    public static final String SCHEMA_VERSION = "1.0";

    public DesignCodeComponentMapping {
        mappingId = requireText(mappingId, "mappingId");
        version = requireText(version, "version");
        if (status == null) throw new IllegalArgumentException("status는 필수입니다.");
        contentHash = ContentHashes.requireValid(contentHash);
        logicalType = requireText(logicalType, "logicalType");
        figmaComponentSetKey = requireText(figmaComponentSetKey, "figmaComponentSetKey");
        thymeleafFragment = requireText(thymeleafFragment, "thymeleafFragment");
        propertyMappings = immutable(propertyMappings);
        slotMappings = immutable(slotMappings);
        fixtureModel = fixtureModel == null ? null : Map.copyOf(fixtureModel);
        supportedRendererProfiles = immutable(supportedRendererProfiles).stream()
                .map(value -> requireText(value, "supportedRendererProfile"))
                .distinct().toList();
        if (supportedRendererProfiles.isEmpty()) {
            throw new IllegalArgumentException("supportedRendererProfiles는 하나 이상이어야 합니다.");
        }
        sourceRevision = requireText(sourceRevision, "sourceRevision");
        approvedBy = normalize(approvedBy);
        if (status == Status.APPROVED && (approvedBy == null || approvedAt == null)) {
            throw new IllegalArgumentException("APPROVED Mapping에는 approvedBy와 approvedAt이 필요합니다.");
        }
        if (status != Status.APPROVED && approvedAt != null && approvedBy == null) {
            throw new IllegalArgumentException("approvedAt이 있으면 approvedBy도 필요합니다.");
        }
        requireUnique(propertyMappings.stream().map(PropertyMapping::figmaProperty).toList(),
                "figmaProperty");
        requireUnique(propertyMappings.stream().map(PropertyMapping::fragmentParameter).toList(),
                "fragmentParameter");
        requireUnique(slotMappings.stream().map(SlotMapping::figmaSlot).toList(), "figmaSlot");
        requireUnique(slotMappings.stream().map(SlotMapping::fragmentSlot).toList(), "fragmentSlot");
    }

    public enum Status { DRAFT, REVIEW_REQUIRED, APPROVED, SUPERSEDED }

    /** 값 변환 정책은 MAP-005에서 실행되며, 이 모델은 승인 가능한 계약 형태만 보존한다. */
    public record PropertyMapping(
            String figmaProperty,
            String fragmentParameter,
            Map<String, Object> valueMapping,
            boolean required,
            @Nullable Object defaultValue,
            @Nullable Object fallbackValue
    ) {
        public PropertyMapping {
            figmaProperty = requireText(figmaProperty, "figmaProperty");
            fragmentParameter = requireText(fragmentParameter, "fragmentParameter");
            valueMapping = valueMapping == null ? Map.of() : Map.copyOf(valueMapping);
        }

        public PropertyMapping(
                String figmaProperty, String fragmentParameter,
                Map<String, Object> valueMapping, boolean required, @Nullable Object defaultValue) {
            this(figmaProperty, fragmentParameter, valueMapping, required, defaultValue, null);
        }
    }

    public record SlotMapping(String figmaSlot, String fragmentSlot) {
        public SlotMapping {
            figmaSlot = requireText(figmaSlot, "figmaSlot");
            fragmentSlot = requireText(fragmentSlot, "fragmentSlot");
        }
    }

    private static void requireUnique(List<String> values, String field) {
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(field + "은 중복될 수 없습니다.");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + "는 필수입니다.");
        return value.trim();
    }

    private static @Nullable String normalize(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static <T> List<T> immutable(@Nullable List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
