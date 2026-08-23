package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignCodeComponentMapping;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DesignCodeComponentMappingCrossValidatorTest {

    private static final String CATALOG_HASH = "b".repeat(64);
    private final DesignCodeComponentMappingCrossValidator validator =
            new DesignCodeComponentMappingCrossValidator(
                    new ComponentRegistryBindingValidator(new ComponentCatalogValidator()));

    @Test
    void catalogRegistryMapping세계약이일치하면통과한다() {
        DesignCodeComponentMappingCrossValidator.ValidationResult result = validator.requireValid(
                catalog(), CATALOG_HASH, registry(binding("BUTTON_SET",
                        ComponentRegistryEntry.LifecycleStatus.CURRENT)), mapping("BUTTON_SET", "revision-1",
                        List.of(label(), completeStyle())), "thymeleaf-krds");

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void figmaKeySourceRevisionRendererProfile불일치를각각차단한다() {
        DesignCodeComponentMappingCrossValidator.ValidationResult result = validator.validate(
                catalog(), CATALOG_HASH, registry(binding("BUTTON_SET",
                        ComponentRegistryEntry.LifecycleStatus.CURRENT)), mapping("OTHER_SET", "revision-2",
                        List.of(label(), completeStyle())), "react");

        assertThat(codes(result)).contains(
                "MAPPING_FIGMA_KEY_MISMATCH",
                "MAPPING_REGISTRY_REVISION_MISMATCH",
                "RENDERER_PROFILE_NOT_SUPPORTED");
        assertThatThrownBy(() -> validator.requireValid(
                catalog(), CATALOG_HASH, registry(binding("BUTTON_SET",
                        ComponentRegistryEntry.LifecycleStatus.CURRENT)), mapping("OTHER_SET", "revision-2",
                        List.of(label(), completeStyle())), "react"))
                .isInstanceOf(DesignCodeComponentMappingCrossValidator.MappingCrossValidationException.class);
    }

    @Test
    void registry는승인되고Current이며같은CatalogHash를참조해야한다() {
        ComponentRegistrySnapshotV3 unapproved = registry(binding("BUTTON_SET",
                ComponentRegistryEntry.LifecycleStatus.DEPRECATED), null, null, "wrong-hash");

        DesignCodeComponentMappingCrossValidator.ValidationResult result = validator.validate(
                catalog(), CATALOG_HASH, unapproved,
                mapping("BUTTON_SET", "revision-1", List.of(label(), completeStyle())),
                "thymeleaf-krds");

        assertThat(codes(result)).contains(
                "CATALOG_HASH_MISMATCH", "UNAPPROVED_REGISTRY", "BINDING_NOT_CURRENT",
                "MAPPING_REGISTRY_BINDING_NOT_CURRENT");
    }

    @Test
    void catalog필수Property와모든허용Variant가Mapping되어야한다() {
        DesignCodeComponentMapping.PropertyMapping incompleteStyle =
                new DesignCodeComponentMapping.PropertyMapping(
                        "Style", "variant", Map.of("Primary", "primary"), false, null, null);

        DesignCodeComponentMappingCrossValidator.ValidationResult result = validator.validate(
                catalog(), CATALOG_HASH, registry(binding("BUTTON_SET",
                        ComponentRegistryEntry.LifecycleStatus.CURRENT)),
                mapping("BUTTON_SET", "revision-1", List.of(incompleteStyle)), "thymeleaf-krds");

        assertThat(codes(result)).contains(
                "REQUIRED_CATALOG_PROPERTY_NOT_MAPPED", "CATALOG_VARIANT_VALUE_NOT_MAPPED");
        assertThat(result.issues()).anySatisfy(issue -> {
            if (issue.code().equals("CATALOG_VARIANT_VALUE_NOT_MAPPED")) {
                assertThat(issue.targetId()).isEqualTo("Style=Secondary");
            }
        });
    }

    @Test
    void catalog에없는Property와Variant값을거부한다() {
        DesignCodeComponentMapping.PropertyMapping unknownProperty =
                new DesignCodeComponentMapping.PropertyMapping(
                        "Elevation", "elevation", Map.of(), false, null, null);
        DesignCodeComponentMapping.PropertyMapping unknownVariant =
                new DesignCodeComponentMapping.PropertyMapping(
                        "Style", "variant",
                        Map.of("Primary", "primary", "Secondary", "secondary", "Danger", "danger"),
                        false, null, null);

        DesignCodeComponentMappingCrossValidator.ValidationResult result = validator.validate(
                catalog(), CATALOG_HASH, registry(binding("BUTTON_SET",
                        ComponentRegistryEntry.LifecycleStatus.CURRENT)),
                mapping("BUTTON_SET", "revision-1", List.of(label(), unknownProperty, unknownVariant)),
                "thymeleaf-krds");

        assertThat(codes(result)).contains(
                "MAPPING_PROPERTY_NOT_IN_CATALOG", "MAPPING_VARIANT_VALUE_NOT_IN_CATALOG");
    }

    @Test
    void 계약입력이없어도NullPointer대신구조화된오류를반환한다() {
        DesignCodeComponentMappingCrossValidator.ValidationResult result =
                validator.validate(null, null, null, null, null);

        assertThat(result.valid()).isFalse();
        assertThat(codes(result)).containsExactly(
                "CATALOG_NULL", "REGISTRY_NULL", "COMPONENT_MAPPING_NULL");
    }

    private List<String> codes(DesignCodeComponentMappingCrossValidator.ValidationResult result) {
        return result.issues().stream().map(DesignSystemIssue::code).toList();
    }

    private ComponentCatalog catalog() {
        ComponentCatalog.Entry button = new ComponentCatalog.Entry(
                ComponentCatalog.Kind.COMPONENT, ComponentCatalog.Requirement.REQUIRED,
                List.of(), null,
                Map.of(
                        "label", new ComponentCatalog.Property(
                                ComponentRegistryEntry.PropertyType.TEXT, "Label", "button.text", Map.of()),
                        "style", new ComponentCatalog.Property(
                                ComponentRegistryEntry.PropertyType.VARIANT, "Style", "button.class",
                                Map.of("primary", "Primary", "secondary", "Secondary"))),
                List.of(), Set.of("action.primary"),
                Set.of(ComponentCatalog.Platform.DESKTOP), Set.of("label"),
                "KrdsButton", null);
        return new ComponentCatalog(ComponentCatalog.SCHEMA_VERSION, "2.0.0",
                Map.of("krds.button", button),
                new ComponentCatalog.FallbackPolicy("FATAL", "PREVIEW_ONLY", "PRESERVE", "FAIL"));
    }

    private ComponentRegistrySnapshotV3 registry(ComponentRegistrySnapshotV3.Binding binding) {
        return registry(binding, "owner", Instant.parse("2026-08-23T01:00:00Z"), CATALOG_HASH);
    }

    private ComponentRegistrySnapshotV3 registry(
            ComponentRegistrySnapshotV3.Binding binding, String approvedBy,
            Instant approvedAt, String catalogHash) {
        return new ComponentRegistrySnapshotV3(
                ComponentRegistrySnapshotV3.SCHEMA_VERSION, "krds", "2.0.0", "3.0.0",
                "2.0.0", catalogHash, new ComponentRegistry.LibraryRef("LIBRARY", "KRDS"),
                Map.of("krds.button", binding), Map.of(), "revision-1",
                approvedBy, approvedAt, "c".repeat(64));
    }

    private ComponentRegistrySnapshotV3.Binding binding(
            String key, ComponentRegistryEntry.LifecycleStatus lifecycle) {
        return new ComponentRegistrySnapshotV3.Binding(
                key, "Button", ComponentRegistryEntry.PublishStatus.CURRENT, lifecycle, Map.of());
    }

    private DesignCodeComponentMapping mapping(
            String figmaKey, String sourceRevision,
            List<DesignCodeComponentMapping.PropertyMapping> properties) {
        return new DesignCodeComponentMapping(
                "map-button", "1.0", DesignCodeComponentMapping.Status.REVIEW_REQUIRED,
                "a".repeat(64), "krds.button", figmaKey, "fragments/button :: button",
                properties, List.of(), null, List.of("thymeleaf-krds"), sourceRevision, null, null);
    }

    private DesignCodeComponentMapping.PropertyMapping label() {
        return new DesignCodeComponentMapping.PropertyMapping(
                "Label", "label", Map.of(), true, null, null);
    }

    private DesignCodeComponentMapping.PropertyMapping completeStyle() {
        return new DesignCodeComponentMapping.PropertyMapping(
                "Style", "variant", Map.of("Primary", "primary", "Secondary", "secondary"),
                false, null, null);
    }
}
