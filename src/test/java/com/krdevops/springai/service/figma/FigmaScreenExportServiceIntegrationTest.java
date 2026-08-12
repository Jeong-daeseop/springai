package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
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
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.service.figma.builder.DetailFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FormFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.ListFigmaScreenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Registry/Profile 엄격 조회와 정확한 버전의 Bundle 조립을 검증한다. */
class FigmaScreenExportServiceIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final LegacyRepositoryDdlProperties ddlProperties = new LegacyRepositoryDdlProperties();
    private final ScreenSpecRepository screenSpecRepository =
            new ScreenSpecRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final FigmaScreenSpecRepository figmaScreenSpecRepository =
            new FigmaScreenSpecRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final DesignSystemProfileRepository profileRepository =
            new DesignSystemProfileRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final ComponentRegistryRepository registryRepository =
            new ComponentRegistryRepository(jdbcTemplate, objectMapper, ddlProperties);

    private final FigmaScreenExportService exportService = new FigmaScreenExportService(
            screenSpecRepository,
            new FigmaScreenBuilderRegistry(List.<FigmaScreenBuilder>of(
                    new ListFigmaScreenBuilder(), new FormFigmaScreenBuilder(), new DetailFigmaScreenBuilder())),
            new FigmaScreenTypeResolver(), new LogicalNodeIdFactory(), new FigmaScreenSpecValidator(),
            profileRepository, registryRepository, figmaScreenSpecRepository,
            new FigmaExportBundleAssembler(), new FigmaScreenSpecSerializer(objectMapper));

    @Test
    void exportWithoutRegistryOrProfileIsRejectedAndNothingIsStored() {
        screenSpecRepository.createTableIfNotExists();
        figmaScreenSpecRepository.createTableIfNotExists();
        profileRepository.createTableIfNotExists();
        registryRepository.createTableIfNotExists();

        String specId = "test-" + UUID.randomUUID();
        ScreenSpecification spec = specWithId(specId);
        screenSpecRepository.save(spec);
        try {
            String missingProfile = "no-such-profile-" + UUID.randomUUID();
            FigmaExportResult result = exportService.export(new FigmaScreenExportRequest(
                    specId, null, "list", missingProfile, "DESKTOP",
                    FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW));

            assertThat(result.status()).isEqualTo(FigmaExportResult.Status.FAILED);
            assertThat(result.figmaScreenSpec()).isNull();
            assertThat(result.issues())
                    .extracting(FigmaExportIssue::code)
                    .contains("PROFILE_NOT_FOUND");
            assertThat(exportService.findLatest("list")).isEmpty();
        } finally {
            jdbcTemplate.update("DELETE FROM AI_SCREEN_SPECIFICATION WHERE SPEC_ID = ?", specId);
            jdbcTemplate.update("DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", "list");
        }
    }

    @Test
    void exportBundleAsJsonProducesSelfContainedDownloadableBundle() {
        screenSpecRepository.createTableIfNotExists();
        figmaScreenSpecRepository.createTableIfNotExists();
        profileRepository.createTableIfNotExists();
        registryRepository.createTableIfNotExists();

        String specId = "test-" + UUID.randomUUID();
        String profileId = "krds-test-" + UUID.randomUUID();
        String profileVersion = "1.0.0";
        String registryVersion = "registry-1";
        screenSpecRepository.save(specWithId(specId));
        profileRepository.save(profile(profileId, profileVersion, registryVersion));
        registryRepository.save(registry(profileId, profileVersion, registryVersion));
        try {
            FigmaScreenExportRequest request = new FigmaScreenExportRequest(
                    specId, null, "list", profileId, "DESKTOP", FigmaExportMode.FINAL, FigmaSyncMode.MERGE);

            FigmaExportBundle bundle = exportService.exportBundle(request);
            String json = exportService.exportBundleAsJson(request);

            assertThat(bundle.figmaScreenSpec().screenId()).isEqualTo("list");
            assertThat(bundle.metadata().designSystemProfileVersion()).isEqualTo(bundle.designSystemProfile().profile().version());
            assertThat(json).contains("\"figmaScreenSpec\"", "\"designSystemProfile\"", "\"componentRegistry\"", "\"metadata\"");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_SCREEN_SPECIFICATION WHERE SPEC_ID = ?", specId);
            jdbcTemplate.update("DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", "list");
            jdbcTemplate.update("DELETE FROM AI_COMPONENT_REGISTRY WHERE PROFILE_ID = ?", profileId);
            jdbcTemplate.update("DELETE FROM AI_DESIGN_SYSTEM_PROFILE WHERE PROFILE_ID = ?", profileId);
        }
    }

    private DesignSystemProfile profile(String profileId, String profileVersion, String registryVersion) {
        return new DesignSystemProfile(
                profileId, "KRDS Test", profileVersion, registryVersion, null,
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
    }

    private ComponentRegistry registry(String profileId, String profileVersion, String registryVersion) {
        return new ComponentRegistry(profileId, profileVersion, registryVersion, null,
                Map.of(
                        "krds.pageHeader", new ComponentRegistryEntry("HEADER_KEY", Map.of()),
                        "krds.searchPanel", new ComponentRegistryEntry("SEARCH_PANEL_KEY", Map.of()),
                        "krds.textField", new ComponentRegistryEntry("TEXT_KEY", Map.of()),
                        "krds.select", new ComponentRegistryEntry("SELECT_KEY", Map.of()),
                        "krds.tableCell", new ComponentRegistryEntry("CELL_KEY", Map.of()),
                        "krds.pagination", new ComponentRegistryEntry("PAGINATION_KEY", Map.of()),
                        "krds.button", new ComponentRegistryEntry("BUTTON_KEY", Map.of())));
    }

    private ScreenSpecification specWithId(String specId) {
        ScreenSpecification base = FigmaBuilderTestFixtures.userManagementSpec();
        return new ScreenSpecification(
                specId, base.version(), base.status(), base.screenName(), base.featureType(), base.archetype(),
                base.database(), base.primaryTable(), base.dataSources(), base.pages(), base.issues(),
                base.layoutDensity(), base.formColumnLayout(), base.actionPlacement(), base.searchPanelPlacement(), null);
    }
}
