package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 승인 Mapping과 후보 Version의 구조적 변경을 순서가 안정적인 Diff로 계산한다. */
@Service
public class DesignCodeComponentMappingDiffService {

    public MappingDiff compare(
            DesignCodeComponentMapping base,
            DesignCodeComponentMapping candidate) {
        if (candidate == null) throw new IllegalArgumentException("candidate Mapping은 필수입니다.");
        if (base == null) {
            return new MappingDiff(null, candidate.version(), true, true,
                    List.of(new MappingChange(Area.MAPPING, candidate.mappingId(), ChangeType.ADDED,
                            List.of("all"), false)));
        }
        List<MappingChange> changes = new ArrayList<>();
        topLevelChanges(base, candidate, changes);
        collectionDiff(Area.PROPERTY, byProperty(base), byProperty(candidate), changes);
        collectionDiff(Area.SLOT, bySlot(base), bySlot(candidate), changes);
        rendererDiff(base.supportedRendererProfiles(), candidate.supportedRendererProfiles(), changes);
        if (!Objects.equals(base.fixtureModel(), candidate.fixtureModel())) {
            changes.add(new MappingChange(Area.FIXTURE, "fixtureModel", ChangeType.MODIFIED,
                    List.of("fixtureModel"), false));
        }
        boolean contentChanged = !base.contentHash().equals(candidate.contentHash());
        return new MappingDiff(base.version(), candidate.version(), false, contentChanged, changes);
    }

    private void topLevelChanges(
            DesignCodeComponentMapping base,
            DesignCodeComponentMapping candidate,
            List<MappingChange> changes) {
        compareField(changes, "mappingId", base.mappingId(), candidate.mappingId(), true);
        compareField(changes, "logicalType", base.logicalType(), candidate.logicalType(), true);
        compareField(changes, "figmaComponentSetKey", base.figmaComponentSetKey(),
                candidate.figmaComponentSetKey(), true);
        compareField(changes, "thymeleafFragment", base.thymeleafFragment(),
                candidate.thymeleafFragment(), true);
        compareField(changes, "sourceRevision", base.sourceRevision(), candidate.sourceRevision(), false);
    }

    private void compareField(
            List<MappingChange> changes, String field, Object before, Object after, boolean breaking) {
        if (!Objects.equals(before, after)) {
            changes.add(new MappingChange(Area.MAPPING, field, ChangeType.MODIFIED,
                    List.of(field), breaking));
        }
    }

    private <T> void collectionDiff(
            Area area, Map<String, T> base, Map<String, T> candidate,
            List<MappingChange> changes) {
        LinkedHashSet<String> keys = new LinkedHashSet<>(base.keySet());
        keys.addAll(candidate.keySet());
        for (String key : keys) {
            T before = base.get(key);
            T after = candidate.get(key);
            if (before == null) {
                changes.add(new MappingChange(area, key, ChangeType.ADDED, List.of("all"), false));
            } else if (after == null) {
                changes.add(new MappingChange(area, key, ChangeType.REMOVED, List.of("all"), true));
            } else if (!before.equals(after)) {
                List<String> fields = area == Area.PROPERTY
                        ? propertyFields((DesignCodeComponentMapping.PropertyMapping) before,
                                (DesignCodeComponentMapping.PropertyMapping) after)
                        : List.of("fragmentSlot");
                boolean breaking = area == Area.SLOT || fields.stream().anyMatch(
                        field -> !field.equals("defaultValue") && !field.equals("fallbackValue"));
                changes.add(new MappingChange(area, key, ChangeType.MODIFIED, fields, breaking));
            }
        }
    }

    private List<String> propertyFields(
            DesignCodeComponentMapping.PropertyMapping base,
            DesignCodeComponentMapping.PropertyMapping candidate) {
        List<String> fields = new ArrayList<>();
        if (!base.fragmentParameter().equals(candidate.fragmentParameter())) fields.add("fragmentParameter");
        if (!base.valueMapping().equals(candidate.valueMapping())) fields.add("valueMapping");
        if (base.required() != candidate.required()) fields.add("required");
        if (!Objects.equals(base.defaultValue(), candidate.defaultValue())) fields.add("defaultValue");
        if (!Objects.equals(base.fallbackValue(), candidate.fallbackValue())) fields.add("fallbackValue");
        return List.copyOf(fields);
    }

    private void rendererDiff(
            List<String> base, List<String> candidate, List<MappingChange> changes) {
        LinkedHashSet<String> removed = new LinkedHashSet<>(base);
        removed.removeAll(candidate);
        removed.forEach(value -> changes.add(new MappingChange(
                Area.RENDERER_PROFILE, value, ChangeType.REMOVED, List.of("support"), true)));
        LinkedHashSet<String> added = new LinkedHashSet<>(candidate);
        added.removeAll(base);
        added.forEach(value -> changes.add(new MappingChange(
                Area.RENDERER_PROFILE, value, ChangeType.ADDED, List.of("support"), false)));
    }

    private Map<String, DesignCodeComponentMapping.PropertyMapping> byProperty(
            DesignCodeComponentMapping mapping) {
        return mapping.propertyMappings().stream().collect(Collectors.toMap(
                DesignCodeComponentMapping.PropertyMapping::figmaProperty, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, DesignCodeComponentMapping.SlotMapping> bySlot(
            DesignCodeComponentMapping mapping) {
        return mapping.slotMappings().stream().collect(Collectors.toMap(
                DesignCodeComponentMapping.SlotMapping::figmaSlot, Function.identity(),
                (left, right) -> left, LinkedHashMap::new));
    }

    public enum Area { MAPPING, PROPERTY, SLOT, RENDERER_PROFILE, FIXTURE }
    public enum ChangeType { ADDED, REMOVED, MODIFIED }

    public record MappingChange(
            Area area,
            String target,
            ChangeType changeType,
            List<String> changedFields,
            boolean breaking
    ) {
        public MappingChange {
            changedFields = List.copyOf(changedFields);
        }
    }

    public record MappingDiff(
            String baseVersion,
            String candidateVersion,
            boolean initialCreation,
            boolean contentChanged,
            List<MappingChange> changes
    ) {
        public MappingDiff {
            changes = List.copyOf(changes);
        }

        public boolean hasBreakingChanges() {
            return changes.stream().anyMatch(MappingChange::breaking);
        }
    }
}
