package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.designsystem.ComponentCatalog;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySnapshotV3;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

class ComponentRegistrySnapshotV3SyncServiceTest {

    private final ComponentCatalogLoader loader = new ComponentCatalogLoader(new ObjectMapper());
    private final ComponentRegistryBindingValidator validator =
            new ComponentRegistryBindingValidator(new ComponentCatalogValidator());
    private final ComponentRegistrySnapshotV3Repository repository = mock(ComponentRegistrySnapshotV3Repository.class);
    private final ComponentRegistrySnapshotV3SyncService service =
            new ComponentRegistrySnapshotV3SyncService(loader, validator, repository);

    @Test
    void unapprovedCandidateCanBePreviewedButCannotBeAppliedWithoutConfirmation() {
        ComponentRegistrySnapshotV3 candidate = candidate();

        assertThat(service.preview(candidate).valid()).isTrue();
        assertThatThrownBy(() -> service.apply(candidate, false, "owner"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmedCandidateIsApprovedAndSavedAsImmutableSnapshot() {
        ComponentRegistrySnapshotV3 candidate = candidate();
        when(repository.findVersion("krds", "3.0.0")).thenReturn(Optional.empty());

        ComponentRegistrySnapshotV3 applied = service.apply(candidate, true, "design-system-owner");

        assertThat(applied.approved()).isTrue();
        assertThat(applied.approvedBy()).isEqualTo("design-system-owner");
        verify(repository).saveImmutable(applied);
    }

    @Test
    void sameCandidateReturnsExistingApprovedSnapshotWithoutRewrite() {
        ComponentRegistrySnapshotV3 candidate = candidate();
        ComponentRegistrySnapshotV3 existing = new ComponentRegistrySnapshotV3(
                candidate.schemaVersion(), candidate.profileId(), candidate.profileVersion(),
                candidate.registryVersion(), candidate.catalogVersion(), candidate.catalogHash(),
                candidate.library(), candidate.bindings(), candidate.variables(), candidate.sourceRevision(),
                "previous-owner", java.time.Instant.parse("2026-08-17T00:00:00Z"), candidate.contentHash());
        when(repository.findVersion("krds", "3.0.0")).thenReturn(Optional.of(existing));

        ComponentRegistrySnapshotV3 applied = service.apply(candidate, true, "new-owner");

        assertThat(applied).isSameAs(existing);
        verify(repository, never()).saveImmutable(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tamperedBindingContentHashIsRejectedBeforePersistence() {
        ComponentRegistrySnapshotV3 original = candidate();
        Map<String, ComponentRegistrySnapshotV3.Binding> tamperedBindings = new LinkedHashMap<>(original.bindings());
        String firstKey = tamperedBindings.keySet().iterator().next();
        ComponentRegistrySnapshotV3.Binding first = tamperedBindings.get(firstKey);
        tamperedBindings.put(firstKey, new ComponentRegistrySnapshotV3.Binding(
                "TAMPERED_SET", first.componentName(), first.publishStatus(), first.lifecycleStatus(), first.variants()));
        ComponentRegistrySnapshotV3 tampered = new ComponentRegistrySnapshotV3(
                original.schemaVersion(), original.profileId(), original.profileVersion(), original.registryVersion(),
                original.catalogVersion(), original.catalogHash(), original.library(),
                tamperedBindings,
                original.variables(), original.sourceRevision(), null, null, original.contentHash());

        assertThatThrownBy(() -> service.apply(tampered, true, "owner"))
                .isInstanceOf(ComponentRegistrySnapshotV3SyncService.RegistryV3RejectedException.class)
                .hasMessageContaining("Catalog 계약");
        verify(repository, never()).saveImmutable(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void publishedComponentKeyChangeRequiresSeparateBreakingApproval() {
        ComponentRegistrySnapshotV3 previous = candidate();
        Map<String, ComponentRegistrySnapshotV3.Binding> changedBindings = new LinkedHashMap<>(previous.bindings());
        String changedKey = changedBindings.keySet().iterator().next();
        ComponentRegistrySnapshotV3.Binding before = changedBindings.get(changedKey);
        changedBindings.put(changedKey, new ComponentRegistrySnapshotV3.Binding(
                "NEW_SET", before.componentName(), before.publishStatus(), before.lifecycleStatus(), before.variants()));
        ComponentRegistrySnapshotV3 changedDraft = new ComponentRegistrySnapshotV3(
                previous.schemaVersion(), previous.profileId(), previous.profileVersion(), "3.1.0",
                previous.catalogVersion(), previous.catalogHash(), previous.library(),
                changedBindings,
                previous.variables(), previous.sourceRevision(), null, null, null);
        ComponentRegistrySnapshotV3 changed = new ComponentRegistrySnapshotV3(
                changedDraft.schemaVersion(), changedDraft.profileId(), changedDraft.profileVersion(),
                changedDraft.registryVersion(), changedDraft.catalogVersion(), changedDraft.catalogHash(),
                changedDraft.library(), changedDraft.bindings(), changedDraft.variables(), changedDraft.sourceRevision(),
                changedDraft.approvedBy(), changedDraft.approvedAt(), service.computeContentHash(changedDraft));
        when(repository.findLatestApproved("krds")).thenReturn(Optional.of(previous));

        assertThatThrownBy(() -> service.apply(changed, true, false, "owner"))
                .isInstanceOf(ComponentRegistrySnapshotV3SyncService.RegistryV3RejectedException.class)
                .hasMessageContaining("Catalog 계약");
        service.apply(changed, true, true, "owner");
        verify(repository).saveImmutable(org.mockito.ArgumentMatchers.any());
    }

    private ComponentRegistrySnapshotV3 candidate() {
        var loaded = loader.load("2.0.0");
        Map<String, ComponentRegistrySnapshotV3.Binding> bindings = new LinkedHashMap<>();
        loaded.catalog().components().forEach((logicalType, entry) -> {
            if (entry.atomicComponent() && entry.requirement() == ComponentCatalog.Requirement.REQUIRED) {
                bindings.put(logicalType, new ComponentRegistrySnapshotV3.Binding(
                        logicalType + "_SET", logicalType,
                        ComponentRegistryEntry.PublishStatus.CURRENT,
                        ComponentRegistryEntry.LifecycleStatus.CURRENT, Map.of()));
            }
        });
        ComponentRegistrySnapshotV3 draft = new ComponentRegistrySnapshotV3(
                ComponentRegistrySnapshotV3.SCHEMA_VERSION, "krds", "2.0.0", "3.0.0",
                loaded.catalog().contractVersion(), loaded.contentHash(),
                new ComponentRegistry.LibraryRef("LIBRARY", "KRDS"), bindings, Map.of(),
                "revision-1", null, null, null);
        return new ComponentRegistrySnapshotV3(
                draft.schemaVersion(), draft.profileId(), draft.profileVersion(), draft.registryVersion(),
                draft.catalogVersion(), draft.catalogHash(), draft.library(), draft.bindings(), draft.variables(),
                draft.sourceRevision(), draft.approvedBy(), draft.approvedAt(), service.computeContentHash(draft));
    }
}
