package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.WebCaptureProperties;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.figma.builder.DetailFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FormFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.ListFigmaScreenBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaScreenExportArtifactIntegrationTest {

    @TempDir
    Path artifactRoot;

    @Test
    void exportPersistsArtifactAndReturnsReference() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ScreenSpecRepository screenRepository = mock(ScreenSpecRepository.class);
        DesignSystemProfileRepository profileRepository = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository registryRepository = mock(ComponentRegistryRepository.class);
        FigmaScreenSpecRepository figmaRepository = mock(FigmaScreenSpecRepository.class);
        when(screenRepository.findLatest("spec-user")).thenReturn(Optional.of(specification()));
        when(figmaRepository.findLatest("user-list")).thenReturn(Optional.empty());
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0.0", "registry-1", null,
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0.0", "registry-1", null,
                Map.of(
                        "krds.pageHeader", new ComponentRegistryEntry("HEADER_KEY", Map.of()),
                        "krds.searchPanel", new ComponentRegistryEntry("SEARCH_PANEL_KEY", Map.of()),
                        "krds.pagination", new ComponentRegistryEntry("PAGINATION_KEY", Map.of()),
                        "krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of())));
        when(profileRepository.findLatest("krds")).thenReturn(Optional.of(profile));
        when(registryRepository.findVersion("krds", "registry-1")).thenReturn(Optional.of(registry));

        WebCaptureProperties properties = new WebCaptureProperties();
        properties.setArtifactBasePath(artifactRoot);
        DesignArtifactService artifactService = new DesignArtifactService(properties, objectMapper);
        FigmaScreenExportService service = new FigmaScreenExportService(
                screenRepository,
                new FigmaScreenBuilderRegistry(List.<FigmaScreenBuilder>of(
                        new ListFigmaScreenBuilder(),
                        new FormFigmaScreenBuilder(),
                        new DetailFigmaScreenBuilder())),
                new FigmaScreenTypeResolver(),
                new LogicalNodeIdFactory(),
                new FigmaScreenSpecValidator(),
                profileRepository,
                registryRepository,
                figmaRepository,
                new FigmaExportBundleAssembler(),
                new FigmaScreenSpecSerializer(objectMapper),
                artifactService);

        var result = service.export(new FigmaScreenExportRequest(
                "spec-user", null, "user-list", null,
                "DESKTOP", FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW));

        assertThat(result.artifactRef()).isNotNull();
        assertThat(Files.isRegularFile(artifactRoot
                .resolve(result.artifactRef().relativePath())
                .resolve("figma-screen-spec.json"))).isTrue();
        verify(figmaRepository).save(result.figmaScreenSpec());
    }

    private ScreenSpecification specification() {
        PageSpec page = new PageSpec(
                "user-list", "CRUD_LIST", List.of(), PageSpec.migrateActions("CREATE"));
        return new ScreenSpecification(
                "spec-user", 1, ScreenSpecStatus.APPROVED,
                "사용자 목록", "CRUD", "CRUD_LIST", "com", "USERS",
                List.of(), List.of(page), List.of(), LocalDateTime.now());
    }
}
