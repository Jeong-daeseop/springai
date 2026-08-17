package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentRegistryMigrationServiceTest {

    @Test
    void previewReportsDroppedPatternAndApplyDelegatesToHumanApprovalSync() {
        ComponentRegistryRepository legacyRepository = mock(ComponentRegistryRepository.class);
        ComponentRegistrySnapshotV3SyncService syncService = mock(ComponentRegistrySnapshotV3SyncService.class);
        ComponentCatalogLoader loader = new ComponentCatalogLoader(new ObjectMapper());
        ComponentRegistry legacy = legacy(loader.load("2.0.0").catalog());
        when(legacyRepository.findVersion("krds", "registry-1")).thenReturn(Optional.of(legacy));
        when(syncService.preview(any())).thenAnswer(invocation -> {
            var candidate = invocation.getArgument(0,
                    com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3.class);
            return new ComponentRegistrySnapshotV3SyncService.Preview(true, List.of(), candidate.catalogHash());
        });
        when(syncService.apply(any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("owner"))).thenAnswer(invocation -> invocation.getArgument(0));
        ComponentRegistryMigrationService service = new ComponentRegistryMigrationService(
                legacyRepository, loader, new ComponentRegistryV2ToV3Converter(new ObjectMapper()), syncService);

        var preview = service.preview("krds", "registry-1", "2.0.0");

        assertThat(preview.valid()).isTrue();
        assertThat(preview.skippedBindings()).containsKey("egov.listPage");
        assertThat(preview.issues()).extracting(issue -> issue.code())
                .contains("NON_ATOMIC_BINDING_DROPPED");

        service.apply("krds", "registry-1", "2.0.0", true, "owner");
        verify(syncService).apply(any(), org.mockito.ArgumentMatchers.eq(true),
                org.mockito.ArgumentMatchers.eq("owner"));
    }

    private ComponentRegistry legacy(ComponentCatalog catalog) {
        Map<String, ComponentRegistryEntry> components = new LinkedHashMap<>();
        catalog.components().forEach((logicalType, contract) -> {
            if (contract.atomicComponent() && contract.requirement() == ComponentCatalog.Requirement.REQUIRED) {
                components.put(logicalType, entry(logicalType));
            }
        });
        components.put("egov.listPage", entry("egov.listPage"));
        return new ComponentRegistry("krds", "2.0.0", "registry-1",
                new ComponentRegistry.LibraryRef("LIBRARY", "KRDS"), components, Map.of());
    }

    private ComponentRegistryEntry entry(String logicalType) {
        return new ComponentRegistryEntry(
                logicalType + "_SET", logicalType, ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(), Map.of(), Map.of());
    }
}
