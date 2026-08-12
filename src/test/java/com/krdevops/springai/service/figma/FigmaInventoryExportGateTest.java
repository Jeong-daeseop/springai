package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FigmaInventoryExportGateTest {
    private final FigmaLibraryInventoryRepository repository = mock(FigmaLibraryInventoryRepository.class);
    private final FigmaInventoryExportGate gate =
            new FigmaInventoryExportGate(repository, new FigmaPropertyDriftValidator());

    @Test
    void finalExportFailsClosedWhenExactInventoryIsMissing() {
        ComponentRegistry registry = registry();
        when(repository.findLatest("krds", "registry-42")).thenReturn(Optional.empty());

        assertThat(gate.validate(registry, component(), FigmaExportMode.FINAL))
                .singleElement().satisfies(issue -> {
                    assertThat(issue.code()).isEqualTo("FIGMA_INVENTORY_SNAPSHOT_MISSING");
                    assertThat(issue.severity()).isEqualTo(com.krdevops.springai.model.figma.FigmaExportIssue.Severity.FATAL);
                });
    }

    @Test
    void actualPropertyTypeDriftBlocksExport() {
        ComponentRegistry registry = registry();
        var actual = new FigmaPropertyDriftValidator.LibraryComponentSnapshot(
                "BUTTON_SET", Map.of("Label", new FigmaPropertyDriftValidator.ActualProperty("BOOLEAN", Set.of())), Map.of());
        when(repository.findLatest("krds", "registry-42")).thenReturn(Optional.of(
                new FigmaLibraryInventorySnapshot("krds", "registry-42", "figma-file-v7",
                        Instant.now(), Map.of("krds.button", actual))));

        assertThat(gate.validate(registry, component(), FigmaExportMode.FINAL))
                .extracting(com.krdevops.springai.model.figma.FigmaExportIssue::code)
                .contains("COMPONENT_PROPERTY_DRIFT");
    }

    private ComponentRegistry registry() {
        var entry = new ComponentRegistryEntry("BUTTON_SET", "Button",
                ComponentRegistryEntry.PublishStatus.CURRENT,
                ComponentRegistryEntry.LifecycleStatus.CURRENT, null, List.of(), Map.of(),
                Map.of("label", new ComponentRegistryEntry.PropertyMapping(
                        "Label", ComponentRegistryEntry.PropertyType.TEXT, Map.of())),
                Set.of(), Set.of(), Map.of(), Set.of(), null, null, "2.1.0");
        return new ComponentRegistry("krds", "profile-3", "registry-42", null,
                Map.of("krds.button", entry));
    }

    private FigmaNodeSpec component() {
        return new FigmaNodeSpec("button-1", FigmaNodeSpec.NodeType.COMPONENT,
                "krds.button", Map.of(), List.of());
    }
}
