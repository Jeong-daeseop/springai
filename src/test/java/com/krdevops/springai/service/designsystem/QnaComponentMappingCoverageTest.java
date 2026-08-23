package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Q&A 7화면을 기준으로 Figma Property→Thymeleaf Fragment Parameter 100% 계약을 고정한다. */
class QnaComponentMappingCoverageTest {

    private static final Path REGISTRY = Path.of(
            "website-figma-contract/fixtures/qna/krds-component-registry-v2.2.2-candidate.json");
    private static final Path QNA_SPEC = Path.of(
            "website-figma-contract/fixtures/qna/qna-screen-specification-v3.json");
    private static final Map<String, String> QNA_COMPONENTS = Map.ofEntries(
            Map.entry("krds.pageHeader", "18b8d3ffdbc9d4824e0d756e87a8f7af684ae980"),
            Map.entry("krds.textField", "f55c5801028025f13b9931f33b111fb21ec2fb39"),
            Map.entry("krds.textarea", "2b440cecd92043fc8347bbab1fc9ab6a90729435"),
            Map.entry("krds.select", "57fa7703261fe74ae982edba68e50e4a7a741485"),
            Map.entry("krds.checkbox", "c1cbdf3a7eefa16b8e2f3d67e36f5df7a0e06d9c"),
            Map.entry("krds.button", "401090e03acd1062033e52471c65fd5f8666c23a"),
            Map.entry("krds.searchPanel", "b49f8e5b0ad1749a6c72b00da8378c71aaeea39b"),
            Map.entry("krds.tableCell", "194a582dc105671593355376a992d90ab42ca7ce"),
            Map.entry("krds.pagination", "0bd34989017991e94c73147e823ef53a6aaac39f"));
    private static final Map<String, List<String>> FRAGMENT_PARAMETERS = Map.ofEntries(
            Map.entry("krds.pageHeader", List.of("title")),
            Map.entry("krds.textField", List.of("label", "placeholder", "size", "state")),
            Map.entry("krds.textarea", List.of("label", "placeholder", "state")),
            Map.entry("krds.select", List.of("label", "placeholder", "size", "state")),
            Map.entry("krds.checkbox", List.of("label", "size", "state", "check")),
            Map.entry("krds.button", List.of("label", "size", "type", "state")),
            Map.entry("krds.searchPanel", List.of("label", "placeholder", "size", "state", "type")),
            Map.entry("krds.tableCell", List.of("type", "label")),
            Map.entry("krds.pagination", List.of()));

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ComponentMappingCoverageValidator validator =
            new ComponentMappingCoverageValidator();

    @Test
    void qna7화면의모든FigmaProperty가FragmentParameter에100퍼센트매핑된다() throws Exception {
        ComponentRegistry registry = objectMapper.readValue(
                Files.readString(REGISTRY), ComponentRegistry.class);
        JsonNode qna = objectMapper.readTree(Files.readString(QNA_SPEC));
        assertThat(qna.path("pages")).hasSize(7);

        var result = validator.validate(
                registry, QNA_COMPONENTS, approvedMappings(registry), "thymeleaf-krds");

        assertThat(result.requiredComponentCount()).isEqualTo(9);
        assertThat(result.totalPropertyCount()).isEqualTo(27);
        assertThat(result.mappedPropertyCount()).isEqualTo(result.totalPropertyCount());
        assertThat(result.coveragePercent()).isEqualTo(100.0);
        assertThat(result.issues()).isEmpty();
        assertThat(result.complete()).isTrue();
    }

    @Test
    void FigmaProperty하나라도빠지면100퍼센트Gate가실패한다() throws Exception {
        ComponentRegistry registry = objectMapper.readValue(
                Files.readString(REGISTRY), ComponentRegistry.class);
        List<DesignCodeComponentMapping> incomplete = new ArrayList<>(approvedMappings(registry));
        DesignCodeComponentMapping textField = incomplete.stream()
                .filter(value -> value.logicalType().equals("krds.textField")).findFirst().orElseThrow();
        incomplete.remove(textField);
        incomplete.add(copyWithout(textField, "Placeholder#286:9"));

        var result = validator.validate(registry, QNA_COMPONENTS, incomplete, "thymeleaf-krds");

        assertThat(result.complete()).isFalse();
        assertThat(result.coveragePercent()).isLessThan(100.0);
        assertThat(result.issues()).anyMatch(issue ->
                issue.code().equals("FIGMA_PROPERTY_UNMAPPED")
                        && issue.target().equals("krds.textField.Placeholder#286:9"));
    }

    private List<DesignCodeComponentMapping> approvedMappings(ComponentRegistry registry) {
        return QNA_COMPONENTS.keySet().stream()
                .map(logicalType -> mapping(logicalType, registry.components().get(logicalType)))
                .toList();
    }

    private DesignCodeComponentMapping mapping(String logicalType, ComponentRegistryEntry entry) {
        List<DesignCodeComponentMapping.PropertyMapping> properties = new ArrayList<>();
        for (String parameter : FRAGMENT_PARAMETERS.get(logicalType)) {
            ComponentRegistryEntry.PropertyMapping registryProperty = entry.properties().get(parameter);
            boolean required = entry.properties().entrySet().stream()
                    .filter(value -> value.getValue().figmaProperty()
                            .equals(registryProperty.figmaProperty()))
                    .anyMatch(value -> entry.requiredProperties().contains(value.getKey()));
            properties.add(new DesignCodeComponentMapping.PropertyMapping(
                    registryProperty.figmaProperty(), parameter,
                    new LinkedHashMap<>(registryProperty.values()), required,
                    required ? sampleValue(registryProperty) : null, null));
        }
        return new DesignCodeComponentMapping(
                "qna-" + logicalType.replace('.', '-'), "1.0",
                DesignCodeComponentMapping.Status.APPROVED, "a".repeat(64), logicalType,
                entry.componentSetKey(), "fragments/krds-components :: "
                        + logicalType.substring(logicalType.indexOf('.') + 1),
                properties, List.of(), Map.of("fixture", "qna"),
                List.of("thymeleaf-krds"), "registry-2.2.2", "qna-contract-test",
                Instant.parse("2026-08-23T01:00:00Z"));
    }

    private Object sampleValue(ComponentRegistryEntry.PropertyMapping property) {
        return property.values().keySet().stream().findFirst().orElse("Q&A");
    }

    private DesignCodeComponentMapping copyWithout(
            DesignCodeComponentMapping source, String figmaProperty) {
        return new DesignCodeComponentMapping(
                source.mappingId(), source.version(), source.status(), source.contentHash(),
                source.logicalType(), source.figmaComponentSetKey(), source.thymeleafFragment(),
                source.propertyMappings().stream()
                        .filter(value -> !value.figmaProperty().equals(figmaProperty)).toList(),
                source.slotMappings(), source.fixtureModel(), source.supportedRendererProfiles(),
                source.sourceRevision(), source.approvedBy(), source.approvedAt());
    }
}
