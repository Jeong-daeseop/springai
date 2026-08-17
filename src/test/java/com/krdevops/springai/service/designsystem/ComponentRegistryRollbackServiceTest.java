package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComponentRegistryRollbackServiceTest {

    private final ComponentRegistrySnapshotV3Repository repository = mock(ComponentRegistrySnapshotV3Repository.class);
    private final ComponentRegistryRollbackService service = new ComponentRegistryRollbackService(repository);

    @Test
    void explicitOperatorConfirmationConnectsApprovedSnapshot() {
        ComponentRegistrySnapshotV3 snapshot = approved();
        when(repository.findVersion("krds", "3.0.0")).thenReturn(Optional.of(snapshot));

        assertThat(service.rollback("krds", "3.0.0", true, "operator")).isSameAs(snapshot);
    }

    @Test
    void rollbackWithoutConfirmationIsRejected() {
        assertThatThrownBy(() -> service.rollback("krds", "3.0.0", false, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("명시적 확인");
    }

    @Test
    void unapprovedTargetIsRejected() {
        ComponentRegistrySnapshotV3 snapshot = new ComponentRegistrySnapshotV3(
                "component-registry-v3", "krds", "2.0.0", "3.0.0", "2.0.0", "a".repeat(64),
                new com.krdevops.springai.model.designsystem.ComponentRegistry.LibraryRef("L", "KRDS"),
                Map.of("button", new ComponentRegistrySnapshotV3.Binding("SET", "Button",
                        com.krdevops.springai.model.designsystem.ComponentRegistryEntry.PublishStatus.CURRENT,
                        com.krdevops.springai.model.designsystem.ComponentRegistryEntry.LifecycleStatus.CURRENT, Map.of())),
                Map.of(), "revision", null, null, "b".repeat(64));
        when(repository.findVersion("krds", "3.0.0")).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() -> service.rollback("krds", "3.0.0", true, "operator"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("승인된 Snapshot");
    }

    private ComponentRegistrySnapshotV3 approved() {
        return new ComponentRegistrySnapshotV3(
                "component-registry-v3", "krds", "2.0.0", "3.0.0", "2.0.0", "a".repeat(64),
                new com.krdevops.springai.model.designsystem.ComponentRegistry.LibraryRef("L", "KRDS"),
                Map.of("button", new ComponentRegistrySnapshotV3.Binding("SET", "Button",
                        com.krdevops.springai.model.designsystem.ComponentRegistryEntry.PublishStatus.CURRENT,
                        com.krdevops.springai.model.designsystem.ComponentRegistryEntry.LifecycleStatus.CURRENT, Map.of())),
                Map.of(), "revision", "owner", Instant.parse("2026-08-17T00:00:00Z"), "b".repeat(64));
    }
}
