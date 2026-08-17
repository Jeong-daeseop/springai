package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComponentRegistryOperationalValidationServiceTest {

    @Test
    void validatesAllApprovedSnapshotsAndReportsEachResult() {
        ComponentRegistrySnapshotV3Repository repository = mock(ComponentRegistrySnapshotV3Repository.class);
        ComponentCatalogLoader loader = new ComponentCatalogLoader(new ObjectMapper());
        ComponentRegistrySnapshotV3SyncService sync = new ComponentRegistrySnapshotV3SyncService(
                loader, new ComponentRegistryBindingValidator(new ComponentCatalogValidator()), repository);
        var loaded = loader.load("2.0.0");
        Map<String, ComponentRegistryEntry> components = new LinkedHashMap<>();
        loaded.catalog().components().forEach((logicalType, contract) -> {
            if (contract.atomicComponent() && contract.requirement() == com.krdevops.springai.model.designsystem.ComponentCatalog.Requirement.REQUIRED) {
                components.put(logicalType, new ComponentRegistryEntry(logicalType + "_SET", logicalType,
                        ComponentRegistryEntry.PublishStatus.CURRENT, Map.of(), Map.of()));
            }
        });
        ComponentRegistry legacy = new ComponentRegistry("krds", "2.0.0", "registry-1",
                new ComponentRegistry.LibraryRef("LIBRARY_KEY", "KRDS Library"), components, Map.of());
        ComponentRegistrySnapshotV3 valid = new ComponentRegistryV2ToV3Converter(new ObjectMapper())
                .convert(legacy, loaded).candidate();
        when(repository.findAllApproved()).thenReturn(List.of(valid));

        var result = new ComponentRegistryOperationalValidationService(repository, loader,
                new ComponentRegistryBindingValidator(new ComponentCatalogValidator()), sync).validateAll();

        assertThat(result.snapshots()).hasSize(1);
        assertThat(result.valid()).isFalse(); // fixture는 승인 메타데이터가 없는 Preview 후보
        assertThat(result.snapshots().get(0).issues()).anyMatch(issue -> issue.code().equals("UNAPPROVED_REGISTRY"));
    }
}
