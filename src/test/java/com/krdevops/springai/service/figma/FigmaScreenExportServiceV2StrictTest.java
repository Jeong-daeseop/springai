package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.service.figma.builder.DetailFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FormFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.ListFigmaScreenBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaScreenExportServiceV2StrictTest {
    @Test
    void productionV2PathRejectsMissingProfileAndDoesNotSaveSpec() {
        ScreenSpecRepository screenRepository = mock(ScreenSpecRepository.class);
        FigmaScreenSpecRepository figmaRepository = mock(FigmaScreenSpecRepository.class);
        DesignSystemProfileRepository profileRepository = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository registryRepository = mock(ComponentRegistryRepository.class);
        ScreenSpecification source = FigmaBuilderTestFixtures.userManagementSpec();
        when(screenRepository.findLatest(source.id())).thenReturn(Optional.of(source));
        when(figmaRepository.findLatest("list")).thenReturn(Optional.empty());
        when(profileRepository.findLatest("missing")).thenReturn(Optional.empty());
        FigmaScreenExportService service = new FigmaScreenExportService(
                screenRepository,
                new FigmaScreenBuilderRegistry(List.<FigmaScreenBuilder>of(
                        new ListFigmaScreenBuilder(), new FormFigmaScreenBuilder(), new DetailFigmaScreenBuilder())),
                new FigmaScreenTypeResolver(), new LogicalNodeIdFactory(), new FigmaScreenSpecValidator(),
                profileRepository, registryRepository, figmaRepository,
                new FigmaExportBundleAssembler(), new FigmaScreenSpecSerializer(new ObjectMapper()));

        FigmaExportResult result = service.export(new FigmaScreenExportRequest(
                source.id(), null, "list", "missing", "DESKTOP", null, null));

        assertThat(result.status()).isEqualTo(FigmaExportResult.Status.FAILED);
        assertThat(result.figmaScreenSpec()).isNull();
        assertThat(result.issues())
                .extracting(FigmaExportIssue::code)
                .contains("PROFILE_NOT_FOUND");
        verify(figmaRepository, never()).save(any());
    }

    @Test
    void productionV2PathRejectsMissingRegistryAndDoesNotSaveSpec() {
        ScreenSpecRepository screenRepository = mock(ScreenSpecRepository.class);
        FigmaScreenSpecRepository figmaRepository = mock(FigmaScreenSpecRepository.class);
        DesignSystemProfileRepository profileRepository = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository registryRepository = mock(ComponentRegistryRepository.class);
        ScreenSpecification source = FigmaBuilderTestFixtures.userManagementSpec();
        DesignSystemProfile profile = profile("1.0.0", "registry-missing");
        when(screenRepository.findLatest(source.id())).thenReturn(Optional.of(source));
        when(figmaRepository.findLatest("list")).thenReturn(Optional.empty());
        when(profileRepository.findLatest("krds")).thenReturn(Optional.of(profile));
        when(registryRepository.findVersion("krds", "registry-missing")).thenReturn(Optional.empty());

        FigmaExportResult result = service(
                screenRepository, figmaRepository, profileRepository, registryRepository)
                .export(new FigmaScreenExportRequest(
                        source.id(), null, "list", "krds", "DESKTOP", null, null));

        assertThat(result.status()).isEqualTo(FigmaExportResult.Status.FAILED);
        assertThat(result.figmaScreenSpec()).isNull();
        assertThat(result.issues())
                .extracting(FigmaExportIssue::code)
                .contains("REGISTRY_NOT_FOUND");
        verify(figmaRepository, never()).save(any());
    }

    @Test
    void bundleUsesProfileVersionReferencedByGeneratedSpec() {
        ScreenSpecRepository screenRepository = mock(ScreenSpecRepository.class);
        FigmaScreenSpecRepository figmaRepository = mock(FigmaScreenSpecRepository.class);
        DesignSystemProfileRepository profileRepository = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository registryRepository = mock(ComponentRegistryRepository.class);
        ScreenSpecification source = FigmaBuilderTestFixtures.userManagementSpec();
        DesignSystemProfile profile = profile("1.0.0", "registry-1");
        ComponentRegistry registry = registry("1.0.0", "registry-1");
        when(screenRepository.findLatest(source.id())).thenReturn(Optional.of(source));
        when(figmaRepository.findLatest("list")).thenReturn(Optional.empty());
        when(profileRepository.findLatest("krds")).thenReturn(Optional.of(profile));
        when(profileRepository.findVersion("krds", "1.0.0")).thenReturn(Optional.of(profile));
        when(registryRepository.findVersion("krds", "registry-1")).thenReturn(Optional.of(registry));

        FigmaExportBundle bundle = service(
                screenRepository, figmaRepository, profileRepository, registryRepository)
                .exportBundle(new FigmaScreenExportRequest(
                        source.id(), null, "list", "krds", "DESKTOP", null, null));

        assertThat(bundle.figmaScreenSpec().designSystem().profileVersion()).isEqualTo("1.0.0");
        assertThat(bundle.designSystemProfile().profile().version()).isEqualTo("1.0.0");
        verify(profileRepository).findVersion("krds", "1.0.0");
    }

    private FigmaScreenExportService service(
            ScreenSpecRepository screenRepository,
            FigmaScreenSpecRepository figmaRepository,
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryRepository registryRepository
    ) {
        return new FigmaScreenExportService(
                screenRepository,
                new FigmaScreenBuilderRegistry(List.<FigmaScreenBuilder>of(
                        new ListFigmaScreenBuilder(), new FormFigmaScreenBuilder(), new DetailFigmaScreenBuilder())),
                new FigmaScreenTypeResolver(), new LogicalNodeIdFactory(), new FigmaScreenSpecValidator(),
                profileRepository, registryRepository, figmaRepository,
                new FigmaExportBundleAssembler(), new FigmaScreenSpecSerializer(new ObjectMapper()));
    }

    private DesignSystemProfile profile(String version, String registryVersion) {
        return new DesignSystemProfile(
                "krds", "KRDS", version, registryVersion, null,
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
    }

    private ComponentRegistry registry(String profileVersion, String registryVersion) {
        return new ComponentRegistry("krds", profileVersion, registryVersion, null,
                Map.of(
                        "krds.pageHeader", new ComponentRegistryEntry("HEADER_KEY", Map.of()),
                        "krds.searchPanel", new ComponentRegistryEntry("SEARCH_PANEL_KEY", Map.of()),
                        "krds.textField", new ComponentRegistryEntry("TEXT_KEY", Map.of()),
                        "krds.select", new ComponentRegistryEntry("SELECT_KEY", Map.of()),
                        "krds.tableCell", new ComponentRegistryEntry("CELL_KEY", Map.of()),
                        "krds.pagination", new ComponentRegistryEntry("PAGINATION_KEY", Map.of()),
                        "krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of())));
    }
}
