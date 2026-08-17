package com.krdevops.springai.service.designsystem;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaReviewHistoryRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DesignSystemRegistryPreflightTest {

    @Test
    void preflightResolvesAliasAndReplacementBeforeScreenGeneration() {
        ComponentRegistryRepository registries = mock(ComponentRegistryRepository.class);
        DesignSystemProfileRepository profiles = mock(DesignSystemProfileRepository.class);
        ComponentRegistryValidator registryValidator = new ComponentRegistryValidator();
        ComponentRegistrySyncService syncService =
                new ComponentRegistrySyncService(registries, profiles, registryValidator);
        DesignSystemQueryService service = new DesignSystemQueryService(
                profiles,
                registries,
                mock(FigmaReviewHistoryRepository.class),
                new DesignSystemSpecValidator(),
                new DesignSystemProfileValidator(),
                registryValidator,
                syncService,
                new ComponentRegistryResolver());

        ComponentRegistry registry = registry();
        DesignSystemProfile profile = new DesignSystemProfile(
                "ftc-krds", "FTC", "1.0.0", "registry-2", "FILE_KEY",
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        when(registries.findVersion("ftc-krds", "registry-2")).thenReturn(Optional.of(registry));
        when(profiles.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(profile));

        DesignSystemQueryService.RegistryPreflightResult result =
                service.preflightRegistry("ftc-krds", "registry-2", List.of("egov.button"));

        assertThat(result.valid()).isTrue();
        assertThat(result.resolutions()).containsEntry("egov.button", "krds.action-button");
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("COMPONENT_REGISTRY_REDIRECT");
    }

    @Test
    void preflightFailsWhenLayoutPolicyVersionDiffersFromExpected() {
        ComponentRegistryRepository registries = mock(ComponentRegistryRepository.class);
        DesignSystemProfileRepository profiles = mock(DesignSystemProfileRepository.class);
        ComponentRegistryValidator registryValidator = new ComponentRegistryValidator();
        ComponentRegistrySyncService syncService =
                new ComponentRegistrySyncService(registries, profiles, registryValidator);
        DesignSystemQueryService service = new DesignSystemQueryService(
                profiles,
                registries,
                mock(FigmaReviewHistoryRepository.class),
                new DesignSystemSpecValidator(),
                new DesignSystemProfileValidator(),
                registryValidator,
                syncService,
                new ComponentRegistryResolver());

        ComponentRegistry registry = registry();
        DesignSystemProfile profile = new DesignSystemProfile(
                "ftc-krds", "FTC", "1.0.0", "registry-2", "FILE_KEY",
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of(), "layout-1", null);
        when(registries.findVersion("ftc-krds", "registry-2")).thenReturn(Optional.of(registry));
        when(profiles.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(profile));
        when(profiles.findLatest("ftc-krds")).thenReturn(Optional.of(profile));

        DesignSystemQueryService.RegistryPreflightResult result =
                service.preflightRegistry("ftc-krds", "registry-2", List.of(), "layout-2");

        assertThat(result.valid()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("LAYOUT_POLICY_VERSION_MISMATCH");
    }

    @Test
    void preflightSkipsLayoutPolicyCheckWhenExpectedVersionIsNull() {
        ComponentRegistryRepository registries = mock(ComponentRegistryRepository.class);
        DesignSystemProfileRepository profiles = mock(DesignSystemProfileRepository.class);
        ComponentRegistryValidator registryValidator = new ComponentRegistryValidator();
        ComponentRegistrySyncService syncService =
                new ComponentRegistrySyncService(registries, profiles, registryValidator);
        DesignSystemQueryService service = new DesignSystemQueryService(
                profiles,
                registries,
                mock(FigmaReviewHistoryRepository.class),
                new DesignSystemSpecValidator(),
                new DesignSystemProfileValidator(),
                registryValidator,
                syncService,
                new ComponentRegistryResolver());

        DesignSystemProfile profile = new DesignSystemProfile(
                "ftc-krds", "FTC", "1.0.0", "registry-2", "FILE_KEY",
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        when(registries.findVersion("ftc-krds", "registry-2")).thenReturn(Optional.of(registry()));
        when(profiles.findVersion("ftc-krds", "1.0.0")).thenReturn(Optional.of(profile));

        DesignSystemQueryService.RegistryPreflightResult result =
                service.preflightRegistry("ftc-krds", "registry-2", List.of());

        assertThat(result.valid()).isTrue();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .doesNotContain("LAYOUT_POLICY_VERSION_MISMATCH");
    }

    private ComponentRegistry registry() {
        return new ComponentRegistry(
                "ftc-krds",
                "1.0.0",
                "registry-2",
                new ComponentRegistry.LibraryRef("FILE_KEY", "FTC Library"),
                Map.of(
                        "krds.button", entry("OLD_KEY", ComponentRegistryEntry.LifecycleStatus.DEPRECATED,
                                "krds.action-button", List.of("egov.button")),
                        "krds.action-button", entry("NEW_KEY", ComponentRegistryEntry.LifecycleStatus.ACTIVE,
                                null, List.of())),
                Map.of());
    }

    private ComponentRegistryEntry entry(
            String key,
            ComponentRegistryEntry.LifecycleStatus lifecycle,
            String replacement,
            List<String> aliases
    ) {
        return new ComponentRegistryEntry(
                key,
                key,
                ComponentRegistryEntry.PublishStatus.CURRENT,
                lifecycle,
                replacement,
                aliases,
                Map.of("Default", key + "_DEFAULT"),
                Map.of());
    }
}
