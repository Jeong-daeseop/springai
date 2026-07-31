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

        assertThat(result.artifact()).isNotNull();
        assertThat(Files.isRegularFile(artifactRoot
                .resolve(result.artifact().relativePath())
                .resolve("figma-screen-spec.json"))).isTrue();
        verify(figmaRepository).save(result.figmaScreenSpec());
    }

    private ScreenSpecification specification() {
        PageSpec page = new PageSpec(
                "user-list", "CRUD_LIST", List.of(), List.of("CREATE"));
        return new ScreenSpecification(
                "spec-user", 1, ScreenSpecStatus.APPROVED,
                "사용자 목록", "CRUD", "CRUD_LIST", "com", "USERS",
                List.of(), List.of(page), List.of(), LocalDateTime.now());
    }
}
