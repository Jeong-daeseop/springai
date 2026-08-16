package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.design.role.ScreenPattern;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.service.designsystem.ComponentRoleResolver;
import com.krdevops.springai.service.designsystem.KrdsRuntimeContractImportService;
import com.krdevops.springai.service.designsystem.VariantRuleResolver;
import com.krdevops.springai.service.figma.builder.DetailFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.FormFigmaScreenBuilder;
import com.krdevops.springai.service.figma.builder.ListFigmaScreenBuilder;
import com.krdevops.springai.service.observability.OperationalTelemetry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 계약 Fixture → MySQL Repository → Resolver → v2 Bundle의 7화면 실행 경로를 검증한다. */
class KrdsQnaRuntimeResolverIntegrationTest {

    private final DriverManagerDataSource dataSource = new DriverManagerDataSource(
            "jdbc:mysql://localhost:3306/ebt?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8",
            System.getenv().getOrDefault("DB_USERNAME", "ebt"),
            System.getenv().getOrDefault("DB_PASSWORD", "ebt01"));
    private final JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final LegacyRepositoryDdlProperties ddlProperties = new LegacyRepositoryDdlProperties();
    private final ComponentRegistryRepository registryRepository =
            new ComponentRegistryRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final ScreenPatternRepository patternRepository =
            new ScreenPatternRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final VariantRuleSetRepository ruleSetRepository =
            new VariantRuleSetRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final FigmaScreenSpecRepository screenSpecRepository =
            new FigmaScreenSpecRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final ScreenSpecRepository businessSpecRepository =
            new ScreenSpecRepository(jdbcTemplate, objectMapper, ddlProperties);
    private final DesignSystemProfileRepository profileRepository =
            new DesignSystemProfileRepository(jdbcTemplate, objectMapper, ddlProperties);

    @Test
    void importsContractsResolvesSixScreensAndProducesPluginBundles() throws Exception {
        registryRepository.createTableIfNotExists();
        patternRepository.createTableIfNotExists();
        ruleSetRepository.createTableIfNotExists();
        screenSpecRepository.createTableIfNotExists();
        businessSpecRepository.createTableIfNotExists();
        profileRepository.createTableIfNotExists();

        KrdsRuntimeContractImportService importer = new KrdsRuntimeContractImportService(
                registryRepository, patternRepository, ruleSetRepository, objectMapper);
        KrdsRuntimeContractImportService.ContractSet source = importer.readDefaultQnaContracts();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String profileId = "krds-qna-it-" + suffix;
        String profileVersion = "2.0.0-it-" + suffix;
        String registryVersion = "2.1.0-it-" + suffix;
        String ruleSetId = "krds-rule-it-" + suffix;
        String ruleSetVersion = "2.0.0-it-" + suffix;

        ComponentRegistry registry = new ComponentRegistry(
                profileId, profileVersion, registryVersion, source.registry().library(),
                source.registry().components(), source.registry().variables());
        VariantRuleSet publishedRules = new VariantRuleSet(
                ruleSetId, ruleSetVersion, profileId, registryVersion,
                VariantRuleSet.Status.PUBLISHED, source.ruleSet().rules());
        List<ScreenPatternDefinition> patterns = source.patterns().stream()
                .map(pattern -> new ScreenPatternDefinition(
                        pattern.pattern(), pattern.version() + "-it-" + suffix, pattern.slots()))
                .toList();
        KrdsRuntimeContractImportService.ContractSet runtimeContracts =
                new KrdsRuntimeContractImportService.ContractSet(patterns, registry, publishedRules);

        List<String> storedScreenIds = new ArrayList<>();
        String businessSpecId = "qna-suite-it-" + suffix;
        try {
            KrdsRuntimeContractImportService.ImportResult imported = importer.importContracts(runtimeContracts);
            assertThat(imported.patternCount()).isEqualTo(4);
            assertThat(registryRepository.findVersion(profileId, registryVersion)).contains(registry);
            assertThat(ruleSetRepository.findPublished(profileId, registryVersion)).contains(publishedRules);
            assertThat(ruleSetRepository.findPublishedVersion(
                    profileId, registryVersion, ruleSetVersion)).contains(publishedRules);

            KrdsComponentResolutionService resolver = new KrdsComponentResolutionService(
                    ruleSetRepository, patternRepository, new ScreenSemanticNormalizer(),
                    new ScreenPatternValidator(), new ComponentRoleResolver(), new VariantRuleResolver(),
                    new OperationalTelemetry(new SimpleMeterRegistry()));
            FigmaExportBundleAssembler assembler = new FigmaExportBundleAssembler();
            FigmaScreenSpecSerializer serializer = new FigmaScreenSpecSerializer(objectMapper);
            FigmaScreenSpecValidator validator = new FigmaScreenSpecValidator();
            FigmaScreenBuilderRegistry builders = new FigmaScreenBuilderRegistry(List.of(
                    new ListFigmaScreenBuilder(), new FormFigmaScreenBuilder(), new DetailFigmaScreenBuilder()));
            FigmaScreenTypeResolver screenTypeResolver = new FigmaScreenTypeResolver();
            LogicalNodeIdFactory logicalNodeIdFactory = new LogicalNodeIdFactory();
            ScreenSpecification sourceBusinessSpec = readBusinessSpecification();
            ScreenSpecification businessSpec = new ScreenSpecification(
                    businessSpecId, sourceBusinessSpec.version(), sourceBusinessSpec.status(),
                    sourceBusinessSpec.screenName(), sourceBusinessSpec.featureType(), sourceBusinessSpec.archetype(),
                    sourceBusinessSpec.database(), sourceBusinessSpec.primaryTable(), sourceBusinessSpec.dataSources(),
                    sourceBusinessSpec.pages(), sourceBusinessSpec.issues(), sourceBusinessSpec.layoutDensity(),
                    sourceBusinessSpec.formColumnLayout(), sourceBusinessSpec.actionPlacement(),
                    sourceBusinessSpec.searchPanelPlacement(), sourceBusinessSpec.createdAt());
            businessSpecRepository.save(businessSpec);
            assertThat(businessSpecRepository.findVersion(businessSpecId, businessSpec.version()))
                    .contains(businessSpec);
            assertThat(businessSpec.pages()).hasSize(7);
            DesignSystemProfile profile = new DesignSystemProfile(
                    profileId, "KRDS Q&A Integration", profileVersion, registryVersion,
                    registry.library().fileKey(), DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
            profileRepository.save(profile);
            Path outputDirectory = Path.of("build", "figma-runtime-qna");
            Files.createDirectories(outputDirectory);
            try (var files = Files.list(outputDirectory)) {
                files.filter(path -> path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> {
                            try { Files.delete(path); }
                            catch (Exception exception) { throw new IllegalStateException(exception); }
                        });
            }

            int resolverSuccessCount = 0;
            int unresolvedCount = 0;
            for (PageSpec page : businessSpec.pages()) {
                var screenType = screenTypeResolver.resolveScreenType(page, businessSpec);
                FigmaNodeSpec semanticRoot = builders.builderFor(screenType)
                        .build(businessSpec, page, logicalNodeIdFactory);
                KrdsComponentResolutionService.ResolutionResult result = resolver.resolve(
                        profileId, registry, page, screenType, businessSpec.layoutDensity(),
                        "DESKTOP", semanticRoot);
                KrdsComponentResolutionService.ResolutionResult repeated = resolver.resolve(
                        profileId, registry, page, screenType, businessSpec.layoutDensity(),
                        "DESKTOP", semanticRoot);
                resolverSuccessCount++;

                assertThat(componentResolutions(result.content()))
                        .as("Runtime Rule ID / Variant Key 결정성: %s", page.id())
                        .containsExactlyInAnyOrderEntriesOf(componentResolutions(repeated.content()));
                assertThat(componentContextHashes(result.content()))
                        .as("결정형 Context Hash: %s", page.id())
                        .containsExactlyInAnyOrderEntriesOf(componentContextHashes(repeated.content()));
                assertThat(result.componentContractVersion())
                        .as("Registry version과 독립적인 Entry contractVersion: %s", page.id())
                        .isEqualTo("2.1.0")
                        .isNotEqualTo(registryVersion);
                List<String> unresolved = unresolvedComponentIds(result.content());
                unresolvedCount += unresolved.size();
                assertThat(unresolved).as("Unresolved Component: %s", page.id()).isEmpty();
                List<FigmaNodeSpec> searchPanels = nodesWithRole(result.content(), "search.panel");
                if (result.pattern() == ScreenPattern.CRUD_LIST) {
                    assertThat(searchPanels).singleElement().satisfies(searchPanel -> {
                        assertThat(searchPanel.nodeType()).isEqualTo(FigmaNodeSpec.NodeType.COMPONENT);
                        assertThat(searchPanel.type()).isEqualTo("krds.searchPanel");
                        assertThat(searchPanel.children()).isEmpty();
                        assertThat(searchPanel.componentResolution().ruleId()).isEqualTo("search-panel-default");
                    });
                } else {
                    assertThat(searchPanels).isEmpty();
                }

                String runtimeScreenId = page.id() + "-it-" + suffix;
                storedScreenIds.add(runtimeScreenId);
                FigmaScreenSpec resolvedSpec = new FigmaScreenSpec(
                        runtimeScreenId, 1, businessSpecId, businessSpec.version(),
                        screenType, screenTypeResolver.resolveLayoutPattern(businessSpec),
                        businessSpec.screenName(), null, "DESKTOP", "APPROVED",
                        new FigmaScreenSpec.DesignSystemRef(profileId, profileVersion, registryVersion),
                        result.content(), List.of(), result.pattern(), result.screenPatternVersion(),
                        result.variantRuleSetVersion(), result.componentContractVersion());
                assertThat(validator.validate(resolvedSpec)).isEmpty();
                screenSpecRepository.save(resolvedSpec);
                assertThat(screenSpecRepository.findVersion(runtimeScreenId, 1))
                        .hasValueSatisfying(stored -> {
                            assertThat(stored.screenId()).isEqualTo(resolvedSpec.screenId());
                            assertThat(stored.semanticPattern()).isEqualTo(resolvedSpec.semanticPattern());
                            assertThat(componentResolutions(stored.content()))
                                    .containsExactlyInAnyOrderEntriesOf(componentResolutions(resolvedSpec.content()));
                        });

                // Resolver가 실제 Repository에서 선택한 Published Snapshot을 Bundle의
                // Source of Truth로 사용한다. 로컬 Fixture 목록을 다시 찾으면 운영 DB에
                // 남아 있는 이전 Pattern Version과 충돌해 조립 대상이 달라질 수 있다.
                ScreenPatternDefinition resolvedPattern = patternRepository
                        .findVersion(result.pattern(), result.screenPatternVersion())
                        .orElseThrow();
                FigmaExportBundle bundle = assembler.assemble(
                        resolvedSpec, profile, registry, resolvedPattern, publishedRules);
                String bundleJson = serializer.toJson(bundle);
                assertThat(bundle.metadata().figmaScreenSpecSchemaVersion())
                        .isEqualTo(FigmaScreenSpec.SCHEMA_VERSION_V2);
                assertThat(bundleJson).contains("\"componentResolution\"", "\"figma-screen-spec-v2\"",
                        "\"screenPattern\"", "\"variantRuleSet\"");
                Files.writeString(outputDirectory.resolve(page.id() + ".json"), bundleJson);
            }
            assertThat(resolverSuccessCount).isEqualTo(7);
            assertThat(unresolvedCount).isZero();
            assertThat(storedScreenIds).hasSize(7);
            try (var files = Files.list(outputDirectory)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".json")).count())
                        .as("Plugin 입력 Root Frame Bundle 수")
                        .isEqualTo(7L);
            }

            FigmaRollbackRehearsalService rehearsal = new FigmaRollbackRehearsalService(
                    businessSpecRepository, profileRepository, registryRepository, patternRepository,
                    ruleSetRepository, builders, screenTypeResolver, logicalNodeIdFactory,
                    new ScreenSemanticNormalizer(), resolver, validator, assembler, objectMapper);
            Map<String, String> patternVersions = new LinkedHashMap<>();
            patterns.forEach(pattern -> patternVersions.put(pattern.pattern().code(), pattern.version()));
            var preview = rehearsal.preview(new FigmaRollbackRehearsalService.RehearsalRequest(
                    businessSpecId, businessSpec.version(), profileId, profileVersion, registryVersion,
                    ruleSetId, ruleSetVersion, patternVersions));
            assertThat(preview.mode()).isEqualTo("PREVIEW_ONLY");
            assertThat(preview.bundleCount()).isEqualTo(7);
            assertThat(preview.contextHashes()).hasSize(7).doesNotContainValue("");
        } finally {
            storedScreenIds.forEach(screenId -> jdbcTemplate.update(
                    "DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", screenId));
            jdbcTemplate.update("DELETE FROM AI_SCREEN_SPECIFICATION WHERE SPEC_ID = ?", businessSpecId);
            jdbcTemplate.update("DELETE FROM AI_VARIANT_RULE_SET WHERE RULE_SET_ID = ? AND RULE_SET_VERSION = ?",
                    ruleSetId, ruleSetVersion);
            jdbcTemplate.update("DELETE FROM AI_COMPONENT_REGISTRY WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ?",
                    profileId, registryVersion);
            jdbcTemplate.update("DELETE FROM AI_DESIGN_SYSTEM_PROFILE WHERE PROFILE_ID = ? AND PROFILE_VERSION = ?",
                    profileId, profileVersion);
            patterns.forEach(pattern -> jdbcTemplate.update(
                    "DELETE FROM AI_SCREEN_PATTERN WHERE PATTERN_ID = ? AND PATTERN_VERSION = ?",
                    pattern.pattern().code(), pattern.version()));
        }
    }

    private ScreenSpecification readBusinessSpecification() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                    "figma/contracts/qna/qna-screen-specification-v3.json");
        try (var input = resource.getInputStream()) {
            return objectMapper.readValue(input, ScreenSpecification.class);
        }
    }

    private Map<String, ResolutionExpectation> componentResolutions(FigmaNodeSpec root) {
        Map<String, ResolutionExpectation> values = new LinkedHashMap<>();
        visit(root, node -> {
            if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT && node.componentResolution() != null) {
                values.put(node.logicalNodeId(), new ResolutionExpectation(
                        node.componentResolution().ruleId(), node.componentResolution().variantKey()));
            }
        });
        return values;
    }

    private Map<String, String> componentContextHashes(FigmaNodeSpec root) {
        Map<String, String> values = new LinkedHashMap<>();
        visit(root, node -> {
            if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT && node.componentResolution() != null) {
                values.put(node.logicalNodeId(), node.componentResolution().contextHash());
            }
        });
        return values;
    }

    private List<String> unresolvedComponentIds(FigmaNodeSpec root) {
        List<String> values = new ArrayList<>();
        visit(root, node -> {
            if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT && node.componentResolution() == null) {
                values.add(node.logicalNodeId());
            }
        });
        return values;
    }

    private List<FigmaNodeSpec> nodesWithRole(FigmaNodeSpec root, String role) {
        List<FigmaNodeSpec> values = new ArrayList<>();
        visit(root, node -> {
            if (role.equals(node.properties().get("semanticRole"))) values.add(node);
        });
        return values;
    }

    private void visit(FigmaNodeSpec node, java.util.function.Consumer<FigmaNodeSpec> visitor) {
        visitor.accept(node);
        node.children().forEach(child -> visit(child, visitor));
    }

    private record ResolutionExpectation(String ruleId, String variantKey) {}
}
