package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryDiff;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.ComponentRegistrySyncResult;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.VariableRegistryEntry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ComponentRegistrySyncServiceTest {

    private final ComponentRegistryRepository registryRepository = mock(ComponentRegistryRepository.class);
    private final DesignSystemProfileRepository profileRepository = mock(DesignSystemProfileRepository.class);
    private final ComponentRegistrySyncService service = new ComponentRegistrySyncService(
            registryRepository, profileRepository, new ComponentRegistryValidator());

    @Test
    void firstPublishedRegistryCanBePreviewedAndAppliedAfterHumanConfirmation() {
        ComponentRegistry candidate = registry("registry-1", "BUTTON_SET_KEY", "VARIABLE_KEY");
        DesignSystemProfile approved = approvedProfile();
        when(profileRepository.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(approved));
        when(registryRepository.findLatest("ftc-krds")).thenReturn(Optional.empty());

        ComponentRegistrySyncResult preview = service.preview(candidate);

        assertThat(preview.diff().valid()).isTrue();
        assertThat(preview.diff().changes())
                .extracting(ComponentRegistryDiff.Change::changeType)
                .containsOnly(ComponentRegistryDiff.ChangeType.ADD);

        ComponentRegistrySyncResult applied = service.apply(candidate, true);

        assertThat(applied.status()).isEqualTo(ComponentRegistrySyncResult.Status.APPLIED);
        assertThat(applied.profile().status()).isEqualTo(DesignSystemProfile.Status.PUBLISHED);
        assertThat(applied.profile().registryVersion()).isEqualTo("registry-1");
        verify(registryRepository).saveImmutable(candidate);
        verify(profileRepository).save(applied.profile());
    }

    @Test
    void changedPublishedComponentKeyIsReportedAsBreakingAndRejected() {
        ComponentRegistry previous = registry("registry-1", "OLD_BUTTON_KEY", "VARIABLE_KEY");
        ComponentRegistry candidate = registry("registry-2", "NEW_BUTTON_KEY", "VARIABLE_KEY");
        when(profileRepository.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(approvedProfile()));
        when(registryRepository.findLatest("ftc-krds")).thenReturn(Optional.of(previous));

        ComponentRegistrySyncResult result = service.apply(candidate, true);

        assertThat(result.status()).isEqualTo(ComponentRegistrySyncResult.Status.REJECTED);
        assertThat(result.diff().issues()).extracting(issue -> issue.code())
                .contains("COMPONENT_KEY_CHANGED");
        verify(registryRepository, never()).saveImmutable(candidate);
    }

    @Test
    void unpublishedOrChangedAssetCannotBeSynchronized() {
        ComponentRegistry candidate = new ComponentRegistry(
                "ftc-krds",
                "1.0.0",
                "registry-1",
                new ComponentRegistry.LibraryRef("mVy5h1UbORVqQoBm8Wr1bT", "FTC 정부 포털 Design System"),
                Map.of("krds.button", new ComponentRegistryEntry(
                        "BUTTON_SET_KEY",
                        "Button",
                        ComponentRegistryEntry.PublishStatus.CHANGED,
                        Map.of("Style=Primary", "BUTTON_PRIMARY_KEY"),
                        Map.of())),
                Map.of());
        when(profileRepository.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(approvedProfile()));
        when(registryRepository.findLatest("ftc-krds")).thenReturn(Optional.empty());

        ComponentRegistrySyncResult preview = service.preview(candidate);

        assertThat(preview.diff().valid()).isFalse();
        assertThat(preview.diff().issues()).extracting(issue -> issue.code())
                .contains("COMPONENT_NOT_CURRENT");
    }

    @Test
    void applyRequiresExplicitHumanConfirmation() {
        assertThatThrownBy(() -> service.apply(registry("registry-1", "BUTTON_SET_KEY", "VARIABLE_KEY"), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("명시적 확인");
    }

    @Test
    void applyingTheSameRegistryVersionAndContentIsIdempotent() {
        ComponentRegistry candidate = registry("registry-1", "BUTTON_SET_KEY", "VARIABLE_KEY");
        when(profileRepository.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(approvedProfile()));
        when(registryRepository.findLatest("ftc-krds")).thenReturn(Optional.of(candidate));
        when(registryRepository.findVersion("ftc-krds", "registry-1")).thenReturn(Optional.of(candidate));

        ComponentRegistrySyncResult result = service.apply(candidate, true);

        assertThat(result.status()).isEqualTo(ComponentRegistrySyncResult.Status.APPLIED);
        verify(registryRepository, never()).saveImmutable(candidate);
        verify(profileRepository).save(result.profile());
    }

    @Test
    void rollbackRequiresHumanConfirmationAndReconnectsStoredRegistryVersion() {
        ComponentRegistry previous = registry("registry-1", "BUTTON_SET_KEY", "VARIABLE_KEY");
        DesignSystemProfile profile = approvedProfile();
        when(registryRepository.findVersion("ftc-krds", "registry-1"))
                .thenReturn(Optional.of(previous));
        when(profileRepository.findVersion("ftc-krds", "1.0.0"))
                .thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.rollbackProfile(
                "ftc-krds", "1.0.0", "registry-1", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("명시적 확인");

        DesignSystemProfile rolledBack = service.rollbackProfile(
                "ftc-krds", "1.0.0", "registry-1", true);

        assertThat(rolledBack.status()).isEqualTo(DesignSystemProfile.Status.PUBLISHED);
        assertThat(rolledBack.registryVersion()).isEqualTo("registry-1");
        assertThat(rolledBack.components().get("krds.button").componentSetKey())
                .isEqualTo("BUTTON_SET_KEY");
        ComponentRegistryResolver.Resolution screenGenerationBinding =
                new ComponentRegistryResolver().resolve(previous, "krds.button");
        assertThat(screenGenerationBinding.resolved()).isTrue();
        assertThat(screenGenerationBinding.entry().componentSetKey())
                .isEqualTo(rolledBack.components().get("krds.button").componentSetKey());
        verify(profileRepository).save(rolledBack);
    }

    @Test
    void incompatiblePropertyRemovalIsRejectedAndReportedAsAtomicFailure() {
        ComponentRegistry previous = registry("registry-1", "BUTTON_SET_KEY", "VARIABLE_KEY");
        ComponentRegistry candidate = new ComponentRegistry(
                "ftc-krds",
                "1.0.0",
                "registry-2",
                previous.library(),
                Map.of("krds.button", new ComponentRegistryEntry(
                        "BUTTON_SET_KEY",
                        "Button renamed",
                        ComponentRegistryEntry.PublishStatus.CURRENT,
                        Map.of("Style=Primary", "BUTTON_PRIMARY_KEY"),
                        Map.of())),
                previous.variables());
        when(profileRepository.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(approvedProfile()));
        when(registryRepository.findLatest("ftc-krds")).thenReturn(Optional.of(previous));

        ComponentRegistrySyncResult result = service.apply(candidate, true);

        assertThat(result.status()).isEqualTo(ComponentRegistrySyncResult.Status.REJECTED);
        assertThat(result.diff().issues()).extracting(issue -> issue.code())
                .contains("COMPONENT_NAME_CHANGED", "COMPONENT_PROPERTY_REMOVED");
        assertThat(result.failureReport()).isNotNull();
        assertThat(result.failureReport().failedTargets()).contains("krds.button");
        verify(registryRepository, never()).saveImmutable(candidate);
    }

    @Test
    void retryRequiresTokenMatchingTheCandidateVersion() {
        ComponentRegistry candidate = registry("registry-2", "BUTTON_SET_KEY", "VARIABLE_KEY");

        assertThatThrownBy(() -> service.retry(candidate, "ftc-krds:registry-1", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("재시도 토큰");
    }

    private ComponentRegistry registry(String registryVersion, String componentKey, String variableKey) {
        return new ComponentRegistry(
                "ftc-krds",
                "1.0.0",
                registryVersion,
                new ComponentRegistry.LibraryRef("mVy5h1UbORVqQoBm8Wr1bT", "FTC 정부 포털 Design System"),
                Map.of("krds.button", new ComponentRegistryEntry(
                        componentKey,
                        "Button",
                        ComponentRegistryEntry.PublishStatus.CURRENT,
                        Map.of("Style=Primary", "BUTTON_PRIMARY_KEY"),
                        Map.of("Style", new ComponentRegistryEntry.PropertyMapping(
                                "Style",
                                ComponentRegistryEntry.PropertyType.VARIANT,
                                Map.of("Primary", "Primary"))))),
                Map.of("color.primary", new VariableRegistryEntry(
                        variableKey,
                        "color.primary",
                        "COLLECTION_KEY",
                        "Foundation",
                        "COLOR",
                        ComponentRegistryEntry.PublishStatus.CURRENT)));
    }

    private DesignSystemProfile approvedProfile() {
        return new DesignSystemProfile(
                "ftc-krds",
                "FTC 정부 포털 Design System",
                "1.0.0",
                "pending",
                "mVy5h1UbORVqQoBm8Wr1bT",
                DesignSystemProfile.Status.APPROVED,
                Map.of(),
                Map.of());
    }
}
