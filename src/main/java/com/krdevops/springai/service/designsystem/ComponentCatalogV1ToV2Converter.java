package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** component-catalog-v1 배열을 v2 logicalType Map으로 변환한다. 승인·배포는 수행하지 않는다. */
@Service
public class ComponentCatalogV1ToV2Converter {

    private final ObjectMapper objectMapper;
    private final ComponentCatalogValidator validator;

    public ComponentCatalogV1ToV2Converter(ObjectMapper objectMapper, ComponentCatalogValidator validator) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.validator = validator;
    }

    public Conversion convert(JsonNode legacy) {
        if (legacy == null || !legacy.isObject()) {
            throw new IllegalArgumentException("Legacy Catalog JSON object가 필요합니다.");
        }
        Map<String, Builder> builders = new LinkedHashMap<>();
        readComponents(legacy.path("requiredComponents"), ComponentCatalog.Requirement.REQUIRED,
                ComponentCatalog.Kind.COMPONENT, builders);
        readComponents(legacy.path("optionalComponents"), ComponentCatalog.Requirement.OPTIONAL,
                ComponentCatalog.Kind.COMPONENT, builders);
        readEntries(legacy.path("patterns"), ComponentCatalog.Requirement.REQUIRED,
                ComponentCatalog.Kind.PATTERN, builders);
        readEntries(legacy.path("pageTemplates"), ComponentCatalog.Requirement.REQUIRED,
                ComponentCatalog.Kind.PAGE_TEMPLATE, builders);
        applyComposition(builders);

        Map<String, ComponentCatalog.Entry> components = new LinkedHashMap<>();
        builders.forEach((logicalType, builder) -> components.put(logicalType, builder.build()));
        ComponentCatalog.FallbackPolicy fallback = new ComponentCatalog.FallbackPolicy(
                text(legacy.path("fallbackPolicy"), "required", "FATAL"),
                text(legacy.path("fallbackPolicy"), "optional", "PREVIEW_ONLY"),
                text(legacy.path("fallbackPolicy"), "unsupportedProperty", "PRESERVE_AS_METADATA"),
                text(legacy.path("fallbackPolicy"), "deprecated", "RESOLVE_REPLACEMENT_OR_FAIL"));
        ComponentCatalog candidate = new ComponentCatalog(
                ComponentCatalog.SCHEMA_VERSION, "2.0.0", components, fallback);
        List<DesignSystemIssue> issues = new ArrayList<>(validator.validate(candidate));
        return new Conversion(candidate, List.copyOf(issues),
                legacy.path("contractVersion").asText("unknown"));
    }

    private void readComponents(JsonNode nodes, ComponentCatalog.Requirement requirement,
            ComponentCatalog.Kind kind, Map<String, Builder> builders) {
        if (!nodes.isArray()) return;
        for (JsonNode node : nodes) {
            String logicalType = node.path("logicalType").asText("");
            if (logicalType.isBlank()) continue;
            Builder builder = builders.computeIfAbsent(logicalType, ignored -> new Builder(kind, requirement));
            builder.requirement = requirement == ComponentCatalog.Requirement.REQUIRED
                    ? ComponentCatalog.Requirement.REQUIRED : builder.requirement;
            readAliases(node.path("aliases"), builder.aliases);
            builder.replacement = nullableText(node, "replacement");
            node.path("figmaProperties").fields().forEachRemaining(property -> {
                String name = property.getKey();
                JsonNode value = property.getValue();
                ComponentRegistryEntry.PropertyType type = ComponentRegistryEntry.PropertyType.valueOf(
                        value.path("type").asText("TEXT"));
                Map<String, String> values = new LinkedHashMap<>();
                value.path("values").fields().forEachRemaining(v -> values.put(v.getKey(), v.getValue().asText()));
                String codeProperty = node.path("codeProperties").path(name).isMissingNode()
                        ? null : node.path("codeProperties").path(name).asText();
                builder.properties.put(name, new ComponentCatalog.Property(
                        type, value.path("figmaProperty").asText(), codeProperty, values));
            });
        }
    }

    private void readEntries(JsonNode nodes, ComponentCatalog.Requirement requirement,
            ComponentCatalog.Kind kind, Map<String, Builder> builders) {
        if (!nodes.isArray()) return;
        for (JsonNode node : nodes) {
            String logicalType = node.path("logicalType").asText("");
            if (logicalType.isBlank()) continue;
            Builder builder = builders.computeIfAbsent(logicalType, ignored -> new Builder(kind, requirement));
            builder.kind = kind;
            builder.requirement = requirement == ComponentCatalog.Requirement.REQUIRED
                    ? ComponentCatalog.Requirement.REQUIRED : builder.requirement;
            readAliases(node.path("aliases"), builder.aliases);
            builder.replacement = nullableText(node, "replacement");
        }
    }

    private void applyComposition(Map<String, Builder> builders) {
        composition(builders, "egov.pageHeader", "krds.pageHeader");
        composition(builders, "egov.dataTable", "krds.tableHeader", "krds.tableCell");
        composition(builders, "egov.formSection", "krds.textField");
        composition(builders, "egov.actionArea", "krds.button");
        composition(builders, "egov.pattern.list", "egov.pageHeader", "krds.searchPanel", "egov.dataTable", "krds.pagination");
        composition(builders, "egov.pattern.form", "egov.pageHeader", "egov.formSection", "egov.actionArea");
        composition(builders, "egov.pattern.detail", "egov.pageHeader", "egov.formSection", "egov.actionArea");
        composition(builders, "egov.pattern.masterDetail", "egov.pattern.list", "egov.pattern.detail");
        composition(builders, "egov.listPage", "egov.pattern.list");
        composition(builders, "egov.formPage", "egov.pattern.form");
        composition(builders, "egov.detailPage", "egov.pattern.detail");
    }

    private void composition(Map<String, Builder> builders, String logicalType, String... targets) {
        Builder builder = builders.get(logicalType);
        if (builder != null) builder.composition.addAll(List.of(targets));
    }

    private void readAliases(JsonNode aliases, Set<String> target) {
        if (aliases.isArray()) aliases.forEach(alias -> target.add(alias.asText()));
    }

    private String nullableText(JsonNode node, String field) {
        return node.path(field).isMissingNode() || node.path(field).isNull()
                ? null : node.path(field).asText();
    }

    private String text(JsonNode parent, String field, String fallback) {
        return parent.path(field).asText(fallback);
    }

    public record Conversion(ComponentCatalog catalog, List<DesignSystemIssue> issues, String sourceVersion) {
        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == DesignSystemIssue.Severity.ERROR
                    || issue.severity() == DesignSystemIssue.Severity.FATAL);
        }
    }

    private static final class Builder {
        private ComponentCatalog.Kind kind;
        private ComponentCatalog.Requirement requirement;
        private final Set<String> aliases = new LinkedHashSet<>();
        private String replacement;
        private final Map<String, ComponentCatalog.Property> properties = new LinkedHashMap<>();
        private final List<String> composition = new ArrayList<>();

        private Builder(ComponentCatalog.Kind kind, ComponentCatalog.Requirement requirement) {
            this.kind = kind;
            this.requirement = requirement;
        }

        private ComponentCatalog.Entry build() {
            Set<String> requiredProperties = requirement == ComponentCatalog.Requirement.REQUIRED
                    ? Set.copyOf(properties.keySet()) : Set.of();
            return new ComponentCatalog.Entry(kind, requirement, List.copyOf(aliases), replacement,
                    properties, List.copyOf(new LinkedHashSet<>(composition)), Set.of(),
                    EnumSet.allOf(ComponentCatalog.Platform.class), requiredProperties, null, null);
        }
    }
}
