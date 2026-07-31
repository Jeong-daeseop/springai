package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportMode;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.FigmaSyncMode;
import com.krdevops.springai.service.figma.builder.DetailFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FormFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.ListFigmaScreenBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** R2-T03: Registry/Profile 미등록 상태에서 FigmaScreenExportService가 fallback 경고와 함께 결과를 만드는지 검증한다. */
class FigmaScreenExportServiceIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private final ScreenSpecRepository screenSpecRepository = new ScreenSpecRepository(jdbcTemplate, objectMapper);
    private final FigmaScreenSpecRepository figmaScreenSpecRepository = new FigmaScreenSpecRepository(jdbcTemplate, objectMapper);
    private final DesignSystemProfileRepository profileRepository = new DesignSystemProfileRepository(jdbcTemplate, objectMapper);
    private final ComponentRegistryRepository registryRepository = new ComponentRegistryRepository(jdbcTemplate, objectMapper);

    private final FigmaScreenExportService exportService = new FigmaScreenExportService(
            screenSpecRepository,
            new FigmaScreenBuilderRegistry(List.<FigmaScreenBuilder>of(
                    new ListFigmaScreenBuilder(), new FormFigmaScreenBuilder(), new DetailFigmaScreenBuilder())),
            new FigmaScreenTypeResolver(), new LogicalNodeIdFactory(), new FigmaScreenSpecValidator(),
            profileRepository, registryRepository, figmaScreenSpecRepository,
            new FigmaExportBundleAssembler(), new FigmaScreenSpecSerializer(objectMapper));

    @Test
    void exportWithoutRegistryOrProfileProducesWarningsButStillSucceeds() {
        screenSpecRepository.createTableIfNotExists();
        figmaScreenSpecRepository.createTableIfNotExists();
        profileRepository.createTableIfNotExists();
        registryRepository.createTableIfNotExists();

        String specId = "test-" + UUID.randomUUID();
        ScreenSpecification spec = specWithId(specId);
        screenSpecRepository.save(spec);
        try {
            FigmaExportResult result = exportService.export(new FigmaScreenExportRequest(
                    specId, null, "list", "no-such-profile-" + UUID.randomUUID(), "DESKTOP",
                    FigmaExportMode.PREVIEW, FigmaSyncMode.PREVIEW));

            assertThat(result.status()).isEqualTo(FigmaExportResult.Status.SUCCESS);
            assertThat(result.figmaScreenSpec().screenType()).isEqualTo(FigmaScreenType.LIST);
            assertThat(result.issues()).extracting(FigmaExportIssue::code).contains("PROFILE_NOT_FOUND");

            assertThat(exportService.findLatest("list")).isPresent();
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
        screenSpecRepository.save(specWithId(specId));
        try {
            FigmaScreenExportRequest request = new FigmaScreenExportRequest(
                    specId, null, "list", null, "DESKTOP", FigmaExportMode.FINAL, FigmaSyncMode.MERGE);

            FigmaExportBundle bundle = exportService.exportBundle(request);
            String json = exportService.exportBundleAsJson(request);

            assertThat(bundle.figmaScreenSpec().screenId()).isEqualTo("list");
            assertThat(bundle.metadata().designSystemProfileVersion()).isEqualTo(bundle.designSystemProfile().profile().version());
            assertThat(json).contains("\"figmaScreenSpec\"", "\"designSystemProfile\"", "\"componentRegistry\"", "\"metadata\"");
        } finally {
            jdbcTemplate.update("DELETE FROM AI_SCREEN_SPECIFICATION WHERE SPEC_ID = ?", specId);
            jdbcTemplate.update("DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", "list");
        }
    }

    private ScreenSpecification specWithId(String specId) {
        ScreenSpecification base = FigmaBuilderTestFixtures.userManagementSpec();
        return new ScreenSpecification(
                specId, base.version(), base.status(), base.screenName(), base.featureType(), base.archetype(),
                base.database(), base.primaryTable(), base.dataSources(), base.pages(), base.issues(),
                base.layoutDensity(), base.formColumnLayout(), base.actionPlacement(), base.searchPanelPlacement(), null);
    }
}
