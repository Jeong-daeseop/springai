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
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.service.DesignArtifactService;
import com.krdevops.springai.service.figma.builder.DetailFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FormFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.ListFigmaScreenBuilder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FigmaScreenExportFailureGateTest {

    @ParameterizedTest
    @EnumSource(value = FigmaExportIssue.Severity.class, names = {"FATAL", "ERROR"})
    void blockingValidationIssueStoresOnlyFailureReport(FigmaExportIssue.Severity severity) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ScreenSpecRepository screenRepository = mock(ScreenSpecRepository.class);
        FigmaScreenSpecRepository figmaRepository = mock(FigmaScreenSpecRepository.class);
        DesignSystemProfileRepository profileRepository = mock(DesignSystemProfileRepository.class);
        ComponentRegistryRepository registryRepository = mock(ComponentRegistryRepository.class);
        FigmaScreenSpecValidator validator = mock(FigmaScreenSpecValidator.class);
        DesignArtifactService artifactService = mock(DesignArtifactService.class);
        ScreenSpecification source = FigmaBuilderTestFixtures.userManagementSpec();
        DesignSystemProfile profile = new DesignSystemProfile(
                "krds", "KRDS", "1.0.0", "registry-1", null,
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        ComponentRegistry registry = new ComponentRegistry(
                "krds", "1.0.0", "registry-1", null,
                Map.of(
                        "krds.pageHeader", new ComponentRegistryEntry("HEADER_KEY", Map.of()),
                        "krds.searchPanel", new ComponentRegistryEntry("SEARCH_PANEL_KEY", Map.of()),
                        "krds.textField", new ComponentRegistryEntry("TEXT_KEY", Map.of()),
                        "krds.select", new ComponentRegistryEntry("SELECT_KEY", Map.of()),
                        "krds.tableCell", new ComponentRegistryEntry("CELL_KEY", Map.of()),
                        "krds.pagination", new ComponentRegistryEntry("PAGINATION_KEY", Map.of()),
                        "krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of())));
        FigmaExportIssue issue = new FigmaExportIssue(
                "TEST_BLOCKING", severity, "차단 검증 오류", "list", "/content", null);

        when(screenRepository.findLatest(source.id())).thenReturn(Optional.of(source));
        when(figmaRepository.findLatest("list")).thenReturn(Optional.empty());
        when(profileRepository.findLatest("krds")).thenReturn(Optional.of(profile));
        when(registryRepository.findVersion("krds", "registry-1")).thenReturn(Optional.of(registry));
        when(validator.validate(any())).thenReturn(List.of(issue));
        when(artifactService.saveFigmaExportFailureReport(anyString(), anyInt(), anyList(), any()))
                .thenReturn(new DesignArtifactService.FigmaExportArtifact(
                        "failure", "figma-export-failures/list/failure", "list", 1, LocalDateTime.now()));

        FigmaScreenExportService service = new FigmaScreenExportService(
                screenRepository,
                new FigmaScreenBuilderRegistry(List.<FigmaScreenBuilder>of(
                        new ListFigmaScreenBuilder(), new FormFigmaScreenBuilder(), new DetailFigmaScreenBuilder())),
                new FigmaScreenTypeResolver(), new LogicalNodeIdFactory(), validator,
                profileRepository, registryRepository, figmaRepository,
                new FigmaExportBundleAssembler(), new FigmaScreenSpecSerializer(objectMapper), artifactService);

        FigmaExportResult result = service.export(new FigmaScreenExportRequest(
                source.id(), null, "list", "krds", "DESKTOP",
                FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW));

        assertThat(result.status()).isEqualTo(FigmaExportResult.Status.FAILED);
        assertThat(result.figmaScreenSpec()).isNull();
        assertThat(result.issues()).containsExactly(issue);
        assertThat(result.artifactRef()).isNotNull();
        verify(figmaRepository, never()).save(any());
        verify(artifactService).saveFigmaExportFailureReport(anyString(), anyInt(), anyList(), any());
        verify(artifactService, never()).saveFigmaExport(any(), any(), anyList(), any());
    }
}
