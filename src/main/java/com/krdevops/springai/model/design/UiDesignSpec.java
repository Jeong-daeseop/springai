package com.krdevops.springai.model.design;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record UiDesignSpec(
        String archetype,
        LayoutSpec layout,
        List<ComponentSpec> components,
        List<ActionSpec> actions,
        List<FieldHint> fieldHints,
        Map<String, String> tokens,
        List<InteractionSpec> interactions,
        List<String> uncertainties
) {
    public static final String SCHEMA_VERSION = "ui-design-spec-v1";

    public UiDesignSpec {
        components = components == null ? List.of() : List.copyOf(components);
        actions = actions == null ? List.of() : List.copyOf(actions);
        fieldHints = fieldHints == null ? List.of() : List.copyOf(fieldHints);
        tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
        interactions = interactions == null ? List.of() : List.copyOf(interactions);
        uncertainties = uncertainties == null ? List.of() : List.copyOf(uncertainties);
    }

    public static UiDesignSpec empty(String archetype) {
        return new UiDesignSpec(archetype, null, List.of(), List.of(), List.of(), Map.of(), List.of(), List.of());
    }

    public record LayoutSpec(
            String shell, String contentWidth, String density, String formColumnLayout,
            String actionPlacement, String searchPanelPlacement) {
        /** actionPlacement/searchPanelPlacement 도입 전 호출자 호환. */
        public LayoutSpec(String shell, String contentWidth, String density, String formColumnLayout) {
            this(shell, contentWidth, density, formColumnLayout, null, null);
        }

        /** formColumnLayout 도입 전 호출자 호환. */
        public LayoutSpec(String shell, String contentWidth, String density) {
            this(shell, contentWidth, density, null, null, null);
        }
    }
    public record ComponentSpec(
            String type, List<String> semanticFields,
            @Nullable String backgroundColor, @Nullable String borderColor) {

        /** 색상 필드 도입 전 호출자 호환. */
        public ComponentSpec(String type, List<String> semanticFields) {
            this(type, semanticFields, null, null);
        }
    }
    public record ActionSpec(String type, String importance) {}
    public record FieldHint(String id, String label, UiFieldRole role, String control, double confidence) {}
    public record InteractionSpec(String trigger, String result) {}
}
