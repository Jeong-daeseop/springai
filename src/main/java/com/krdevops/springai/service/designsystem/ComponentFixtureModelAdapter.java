package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentFixtureModel;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Canonical Fixture Envelope와 기존 평면 Fragment Parameter Fixture를 하나의 계약으로 변환한다. */
@Service
public class ComponentFixtureModelAdapter {

    private static final Set<String> ENVELOPE_KEYS = Set.of(
            "schemaVersion", "figmaProperties", "figmaSlots", "contextVariables");

    public Adaptation adapt(DesignCodeComponentMapping mapping) {
        if (mapping == null) throw new IllegalArgumentException("mapping은 필수입니다.");
        Map<String, Object> raw = mapping.fixtureModel();
        if (raw == null || raw.isEmpty()) {
            return new Adaptation(null, false, List.of(new FixtureIssue(
                    "FIXTURE_MODEL_MISSING", Severity.ERROR,
                    "Component Mapping에 Fixture Model이 없습니다.", mapping.mappingId())));
        }
        boolean envelope = raw.keySet().stream().anyMatch(ENVELOPE_KEYS::contains);
        return envelope ? canonical(raw) : legacy(mapping, raw);
    }

    private Adaptation canonical(Map<String, Object> raw) {
        List<FixtureIssue> issues = new ArrayList<>();
        String schemaVersion = raw.get("schemaVersion") == null
                ? ComponentFixtureModel.SCHEMA_VERSION : String.valueOf(raw.get("schemaVersion"));
        Map<String, Object> properties = objectMap(raw.get("figmaProperties"), "figmaProperties", issues);
        Map<String, Object> slots = objectMap(raw.get("figmaSlots"), "figmaSlots", issues);
        Map<String, Object> context = objectMap(raw.get("contextVariables"), "contextVariables", issues);
        raw.keySet().stream().filter(key -> !ENVELOPE_KEYS.contains(key)).forEach(key ->
                issues.add(new FixtureIssue("FIXTURE_ENVELOPE_FIELD_UNKNOWN", Severity.WARNING,
                        "Fixture Envelope의 알 수 없는 필드입니다.", key)));
        try {
            return new Adaptation(new ComponentFixtureModel(schemaVersion, properties, slots, context),
                    false, issues);
        } catch (IllegalArgumentException exception) {
            String code = exception.getMessage() != null && exception.getMessage().contains("schemaVersion")
                    ? "FIXTURE_SCHEMA_UNSUPPORTED" : "FIXTURE_MODEL_INVALID";
            issues.add(new FixtureIssue(code, Severity.ERROR,
                    exception.getMessage(), schemaVersion));
            return new Adaptation(null, false, issues);
        }
    }

    private Adaptation legacy(
            DesignCodeComponentMapping mapping,
            Map<String, Object> raw) {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        LinkedHashMap<String, Object> slots = new LinkedHashMap<>();
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        List<FixtureIssue> issues = new ArrayList<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
                issues.add(new FixtureIssue("FIXTURE_ENTRY_INVALID", Severity.ERROR,
                        "평면 Fixture 항목은 문자열 Key와 null이 아닌 값이 필요합니다.",
                        String.valueOf(entry.getKey())));
                continue;
            }
            DesignCodeComponentMapping.PropertyMapping property = mapping.propertyMappings().stream()
                    .filter(value -> value.fragmentParameter().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (property != null) {
                Object figmaValue = reverseValue(property, entry.getValue(), issues);
                if (figmaValue != null) properties.put(property.figmaProperty(), figmaValue);
                continue;
            }
            DesignCodeComponentMapping.SlotMapping slot = mapping.slotMappings().stream()
                    .filter(value -> value.fragmentSlot().equals(entry.getKey()))
                    .findFirst().orElse(null);
            if (slot != null) slots.put(slot.figmaSlot(), entry.getValue());
            else context.put(entry.getKey(), entry.getValue());
        }
        issues.add(new FixtureIssue("LEGACY_FIXTURE_ADAPTED", Severity.WARNING,
                "평면 Fragment Parameter Fixture를 Canonical Figma 입력 Fixture로 변환했습니다.",
                mapping.mappingId()));
        return new Adaptation(new ComponentFixtureModel(
                ComponentFixtureModel.SCHEMA_VERSION, properties, slots, context), true, issues);
    }

    private Object reverseValue(
            DesignCodeComponentMapping.PropertyMapping property,
            Object fragmentValue,
            List<FixtureIssue> issues) {
        if (property.valueMapping().isEmpty()) return fragmentValue;
        List<String> matches = property.valueMapping().entrySet().stream()
                .filter(entry -> java.util.Objects.equals(entry.getValue(), fragmentValue))
                .map(Map.Entry::getKey).toList();
        if (matches.size() == 1) return matches.get(0);
        if (property.valueMapping().containsKey(String.valueOf(fragmentValue))) return fragmentValue;
        issues.add(new FixtureIssue("LEGACY_FIXTURE_VALUE_NOT_REVERSIBLE", Severity.ERROR,
                "평면 Fixture 값을 단일 Figma Variant 값으로 역변환할 수 없습니다.",
                property.fragmentParameter()));
        return null;
    }

    private Map<String, Object> objectMap(
            Object value, String field, List<FixtureIssue> issues) {
        if (value == null) return Map.of();
        if (!(value instanceof Map<?, ?> source)) {
            issues.add(new FixtureIssue("FIXTURE_FIELD_NOT_OBJECT", Severity.ERROR,
                    "Fixture Envelope 필드는 Object여야 합니다.", field));
            return Map.of();
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> {
            if (!(key instanceof String text) || text.isBlank() || item == null) {
                issues.add(new FixtureIssue("FIXTURE_ENTRY_INVALID", Severity.ERROR,
                        "Fixture 항목은 비어 있지 않은 문자열 Key와 null이 아닌 값을 사용해야 합니다.", field));
            } else {
                result.put(text, item);
            }
        });
        return result;
    }

    public record Adaptation(
            ComponentFixtureModel fixture,
            boolean legacyAdapted,
            List<FixtureIssue> issues
    ) {
        public Adaptation {
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return fixture != null && issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }
    }

    public record FixtureIssue(String code, Severity severity, String message, String target) {}

    public enum Severity { WARNING, ERROR }
}
