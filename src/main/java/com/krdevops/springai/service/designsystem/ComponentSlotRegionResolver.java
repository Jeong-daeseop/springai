package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Figma Instance/Content Slot을 승인된 Thymeleaf Fragment 영역명으로 투영한다. */
@Service
public class ComponentSlotRegionResolver {

    public Resolution resolve(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaSlots) {
        return resolve(mapping, figmaSlots, true);
    }

    /** 승인 전 Fixture Preview에서 동일 Slot 변환 규칙을 쓰되 Mapping 상태 Gate만 유예한다. */
    public Resolution resolveCandidate(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaSlots) {
        return resolve(mapping, figmaSlots, false);
    }

    private Resolution resolve(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaSlots,
            boolean requireApproved) {
        if (mapping == null) throw new IllegalArgumentException("mapping은 필수입니다.");
        Map<String, ?> input = figmaSlots == null ? Map.of() : figmaSlots;
        LinkedHashMap<String, Object> regions = new LinkedHashMap<>();
        LinkedHashSet<String> consumed = new LinkedHashSet<>();
        LinkedHashSet<String> missing = new LinkedHashSet<>();
        List<ResolutionIssue> issues = new ArrayList<>();

        if (requireApproved && mapping.status() != DesignCodeComponentMapping.Status.APPROVED) {
            issues.add(new ResolutionIssue("MAPPING_NOT_APPROVED", Severity.ERROR,
                    "APPROVED Component Mapping만 Fragment Slot 해석에 사용할 수 있습니다.",
                    mapping.mappingId()));
        }

        for (DesignCodeComponentMapping.SlotMapping slot : mapping.slotMappings()) {
            Object content = input.get(slot.figmaSlot());
            if (!input.containsKey(slot.figmaSlot()) || content == null) {
                missing.add(slot.figmaSlot());
                issues.add(new ResolutionIssue("MAPPED_SLOT_NOT_PRESENT", Severity.INFO,
                        "Mapping에 선언됐지만 현재 Component Instance에는 내용이 없는 Slot입니다.",
                        slot.figmaSlot()));
                continue;
            }
            consumed.add(slot.figmaSlot());
            regions.put(slot.fragmentSlot(), content);
        }

        LinkedHashSet<String> unmapped = new LinkedHashSet<>(input.keySet());
        unmapped.removeAll(consumed);
        unmapped.removeAll(missing);
        for (String slot : unmapped) {
            issues.add(new ResolutionIssue("UNMAPPED_FIGMA_SLOT", Severity.WARNING,
                    "Fragment 영역에 연결되지 않은 Figma Slot입니다.", slot));
        }

        return new Resolution(mapping.mappingId(), mapping.version(), mapping.thymeleafFragment(),
                regions, consumed, missing, unmapped, issues);
    }

    /** Apply 경계에서 승인되지 않은 Slot Mapping 사용을 fail-closed로 차단한다. */
    public Resolution requireResolved(
            DesignCodeComponentMapping mapping,
            Map<String, ?> figmaSlots) {
        Resolution resolution = resolve(mapping, figmaSlots);
        if (!resolution.valid()) throw new ComponentSlotResolutionException(resolution);
        return resolution;
    }

    public record Resolution(
            String mappingId,
            String mappingVersion,
            String thymeleafFragment,
            Map<String, Object> fragmentRegions,
            Set<String> consumedFigmaSlots,
            Set<String> missingMappedSlots,
            Set<String> unmappedFigmaSlots,
            List<ResolutionIssue> issues
    ) {
        public Resolution {
            fragmentRegions = Collections.unmodifiableMap(new LinkedHashMap<>(fragmentRegions));
            consumedFigmaSlots = immutableSet(consumedFigmaSlots);
            missingMappedSlots = immutableSet(missingMappedSlots);
            unmappedFigmaSlots = immutableSet(unmappedFigmaSlots);
            issues = List.copyOf(issues);
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR);
        }

        private static <T> Set<T> immutableSet(Set<T> source) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(source));
        }
    }

    public record ResolutionIssue(String code, Severity severity, String message, String target) {
        public ResolutionIssue {
            if (code == null || code.isBlank()) throw new IllegalArgumentException("issue code는 필수입니다.");
            if (severity == null) throw new IllegalArgumentException("issue severity는 필수입니다.");
            if (message == null || message.isBlank()) throw new IllegalArgumentException("issue message는 필수입니다.");
        }
    }

    public enum Severity { INFO, WARNING, ERROR }

    public static final class ComponentSlotResolutionException extends IllegalStateException {
        private final Resolution resolution;

        public ComponentSlotResolutionException(Resolution resolution) {
            super("Component Slot을 Fragment 영역으로 해석할 수 없습니다: "
                    + resolution.mappingId() + "@" + resolution.mappingVersion());
            this.resolution = resolution;
        }

        public Resolution resolution() {
            return resolution;
        }
    }
}
