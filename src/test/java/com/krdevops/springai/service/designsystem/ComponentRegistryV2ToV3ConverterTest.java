package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentRegistryV2ToV3ConverterTest {

    private final ComponentCatalogLoader loader = new ComponentCatalogLoader(new ObjectMapper());
    private final ComponentRegistryV2ToV3Converter converter =
            new ComponentRegistryV2ToV3Converter(new ObjectMapper());

    @Test
    void conversionIsDeterministicAndDropsNonAtomicBindings() {
        var catalog = loader.load("2.0.0");
        ComponentRegistry legacy = legacy(catalog.catalog());

        var first = converter.convert(legacy, catalog);
        var second = converter.convert(legacy, catalog);

        assertThat(first.candidate().contentHash()).matches("[a-f0-9]{64}")
                .isEqualTo(second.candidate().contentHash());
        assertThat(first.candidate().approved()).isFalse();
        assertThat(first.candidate().catalogHash()).isEqualTo(catalog.contentHash());
        assertThat(first.skippedBindings()).containsEntry("egov.listPage", "NON_ATOMIC_CATALOG_ENTRY");
        assertThat(first.candidate().bindings()).doesNotContainKey("egov.listPage");
    }

    @Test
    void convertedCompleteRegistryPassesCatalogCrossValidationBeforeApproval() {
        var catalog = loader.load("2.0.0");
        var conversion = converter.convert(legacy(catalog.catalog()), catalog);
        ComponentRegistryBindingValidator validator =
                new ComponentRegistryBindingValidator(new ComponentCatalogValidator());

        assertThat(validator.validate(catalog.catalog(), catalog.contentHash(),
                conversion.candidate(), false)).isEmpty();
    }

    private ComponentRegistry legacy(ComponentCatalog catalog) {
        Map<String, ComponentRegistryEntry> components = new LinkedHashMap<>();
        catalog.components().forEach((logicalType, contract) -> {
            if (contract.atomicComponent() && contract.requirement() == ComponentCatalog.Requirement.REQUIRED) {
                components.put(logicalType, entry(logicalType));
            }
        });
        components.put("egov.listPage", entry("egov.listPage"));
        return new ComponentRegistry(
                "krds", "2.0.0", "registry-1",
                new ComponentRegistry.LibraryRef("LIBRARY_KEY", "KRDS Library"), components, Map.of());
    }

    private ComponentRegistryEntry entry(String logicalType) {
        return new ComponentRegistryEntry(
                logicalType + "_SET", logicalType, ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.ACTIVE, null, java.util.List.of(),
                Map.of(), Map.of());
    }
}
