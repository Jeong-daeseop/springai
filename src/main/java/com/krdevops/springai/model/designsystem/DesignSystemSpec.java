package com.krdevops.springai.model.designsystem;

import java.util.List;
import java.util.Map;

/**
 * 에이전트가 생성하는 디자인 시스템 명세(09번 §4.2). Author Plugin이 이 명세를 읽어
 * Figma Variables·Components·Patterns를 제자리 생성·갱신한다.
 */
public record DesignSystemSpec(
        String id,
        String name,
        String version,
        List<Token> tokens,
        List<VariableCollection> variableCollections,
        List<ComponentDefinition> components,
        List<PatternDefinition> patterns,
        List<DesignSystemIssue> issues
) {
    public static final String SCHEMA_VERSION = "design-system-spec-v1";

    public DesignSystemSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id는 필수입니다.");
        }
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        variableCollections = variableCollections == null ? List.of() : List.copyOf(variableCollections);
        components = components == null ? List.of() : List.copyOf(components);
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /** category: COLOR/SPACING/TYPOGRAPHY/RADIUS/ELEVATION 등. */
    public record Token(String category, String name, String value) {}

    /** modes 예: ["Light","Dark","HighContrast"]. valuesByMode: 변수명 -> 모드 -> 값. */
    public record VariableCollection(
            String name,
            List<String> modes,
            Map<String, Map<String, String>> valuesByMode) {}

    public record ComponentDefinition(
            String id,
            String name,
            String description,
            DeveloperMetadata developer,
            LifecycleStatus lifecycleStatus,
            String replacementLogicalType,
            List<String> aliases,
            Layout layout,
            List<Property> properties,
            Map<String, List<String>> variants) {

        public ComponentDefinition {
            lifecycleStatus = lifecycleStatus == null ? LifecycleStatus.ACTIVE : lifecycleStatus;
            aliases = aliases == null ? List.of() : List.copyOf(aliases);
        }

        public ComponentDefinition(
                String id,
                String name,
                String description,
                DeveloperMetadata developer,
                Layout layout,
                List<Property> properties,
                Map<String, List<String>> variants) {
            this(id, name, description, developer, LifecycleStatus.ACTIVE,
                    null, List.of(), layout, properties, variants);
        }

        public ComponentDefinition(
                String id,
                String name,
                Layout layout,
                List<Property> properties,
                Map<String, List<String>> variants) {
            this(id, name, null, null, LifecycleStatus.ACTIVE,
                    null, List.of(), layout, properties, variants);
        }

        public record DeveloperMetadata(
                String codeComponent,
                String documentationUrl,
                String packageName) {}

        public record Layout(
                String mode,
                String paddingX,
                String paddingY,
                String gap,
                String alignment,
                String minWidth,
                String maxWidth,
                String minHeight,
                String maxHeight) {

            public Layout(
                    String mode,
                    String paddingX,
                    String paddingY,
                    String gap,
                    String alignment) {
                this(mode, paddingX, paddingY, gap, alignment, null, null, null, null);
            }
        }

        public record Property(String name, PropertyType type, String defaultValue) {}

        public enum PropertyType { TEXT, BOOLEAN, VARIANT, INSTANCE_SWAP }

        public enum LifecycleStatus { ACTIVE, DEPRECATED }
    }

    public record PatternDefinition(String id, String name, List<String> composedOf) {}
}
