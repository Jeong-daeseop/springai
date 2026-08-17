package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.DesignSystemIssue;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentCatalogSsotTest {

    private final ComponentCatalogLoader loader = new ComponentCatalogLoader(new ObjectMapper());
    private final ComponentCatalogValidator catalogValidator = new ComponentCatalogValidator();
    private final ComponentRegistryBindingValidator bindingValidator =
            new ComponentRegistryBindingValidator(catalogValidator);

    @Test
    void catalogLoadsWithDeterministicHashAndValidComposition() throws Exception {
        var first = loader.load("2.0.0");
        var second = loader.load("2.0.0");

        assertThat(first.contentHash()).matches("[a-f0-9]{64}");
        assertThat(second.contentHash()).isEqualTo(first.contentHash());
        assertThat(catalogValidator.validate(first.catalog())).isEmpty();
        assertThat(first.catalog().components().get("egov.dataTable").composition())
                .containsExactly("krds.tableHeader", "krds.tableCell");
        assertThat(new ObjectMapper().findAndRegisterModules().readValue(
                new ObjectMapper().findAndRegisterModules().writeValueAsBytes(first.catalog()),
                ComponentCatalog.class)).isEqualTo(first.catalog());
    }

    @Test
    void exactCatalogVersionIsRequired() {
        assertThatThrownBy(() -> loader.load("9.9.9"))
                .isInstanceOf(ComponentCatalogLoader.ComponentCatalogException.class)
                .extracting("code").isEqualTo("CATALOG_VERSION_NOT_FOUND");
    }

    @Test
    void registryMustMatchCatalogHashAndContainRequiredAtomicBindings() {
        var loaded = loader.load("2.0.0");
        ComponentRegistrySnapshotV3 registry = registry(loaded.catalog(), "bad-hash", false);

        assertThat(bindingValidator.validate(loaded.catalog(), loaded.contentHash(), registry))
                .extracting(DesignSystemIssue::code)
                .contains("CATALOG_HASH_MISMATCH", "REQUIRED_BINDING_MISSING");
    }

    @Test
    void dataTablePatternResolvesToTwoPublishedAtomicBindings() {
        var loaded = loader.load("2.0.0");
        ComponentRegistrySnapshotV3 registry = registry(loaded.catalog(), loaded.contentHash(), true);
        ResolvedComponentRegistryService service =
                new ResolvedComponentRegistryService(loader, bindingValidator);

        var resolved = service.resolve(registry, Set.of("egov.dataTable", "pageTitle"));

        assertThat(resolved.entries().get("egov.dataTable").atomicBindings())
                .extracting(binding -> binding.logicalType())
                .containsExactly("krds.tableHeader", "krds.tableCell");
        assertThat(resolved.entries().get("pageTitle").canonicalLogicalType())
                .isEqualTo("krds.pageHeader");
    }

    @Test
    void registryVariantMustBeDeclaredByCatalogPropertyContract() {
        var loaded = loader.load("2.0.0");
        ComponentRegistrySnapshotV3 base = registry(loaded.catalog(), loaded.contentHash(), true);
        Map<String, ComponentRegistrySnapshotV3.Binding> bindings = new LinkedHashMap<>(base.bindings());
        var button = bindings.get("krds.button");
        bindings.put("krds.button", new ComponentRegistrySnapshotV3.Binding(button.componentSetKey(), button.componentName(),
                button.publishStatus(), button.lifecycleStatus(), Map.of("ghost", "GHOST_KEY")));
        ComponentRegistrySnapshotV3 invalid = new ComponentRegistrySnapshotV3(base.schemaVersion(), base.profileId(),
                base.profileVersion(), base.registryVersion(), base.catalogVersion(), base.catalogHash(), base.library(),
                bindings, base.variables(), base.sourceRevision(), base.approvedBy(), base.approvedAt(), base.contentHash());

        assertThat(bindingValidator.validate(loaded.catalog(), loaded.contentHash(), invalid))
                .extracting(DesignSystemIssue::code).contains("VARIANT_CONTRACT_MISMATCH");
    }

    private ComponentRegistrySnapshotV3 registry(ComponentCatalog catalog, String catalogHash, boolean complete) {
        Map<String, ComponentRegistrySnapshotV3.Binding> bindings = new LinkedHashMap<>();
        catalog.components().forEach((logicalType, entry) -> {
            if (entry.atomicComponent() && entry.requirement() == ComponentCatalog.Requirement.REQUIRED
                    && (complete || !logicalType.equals("krds.button"))) {
                bindings.put(logicalType, new ComponentRegistrySnapshotV3.Binding(
                        logicalType + "_SET_KEY", logicalType,
                        ComponentRegistryEntry.PublishStatus.CURRENT,
                        ComponentRegistryEntry.LifecycleStatus.CURRENT,
                        Map.of()));
            }
        });
        return new ComponentRegistrySnapshotV3(
                ComponentRegistrySnapshotV3.SCHEMA_VERSION,
                "krds", "2.0.0", "3.0.0", catalog.contractVersion(), catalogHash,
                new ComponentRegistry.LibraryRef("LIBRARY_KEY", "KRDS Library"),
                bindings, Map.of(), "revision-1", "owner", Instant.parse("2026-08-17T00:00:00Z"),
                "1".repeat(64));
    }
}
