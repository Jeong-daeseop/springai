package com.krdevops.springai.service.figma;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.config.LegacyRepositoryDdlProperties;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.design.LayoutDensity;
import com.krdevops.springai.model.design.PageSpec;
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

/** 실제 계약 Fixture → MySQL Repository → Resolver → v2 Bundle의 6화면 실행 경로를 검증한다. */
class KrdsQnaRuntimeResolverIntegrationTest {

    private static final List<String> SCREEN_FILES = List.of(
            "qna-list.json", "qna-create.json", "qna-detail.json",
            "qna-answer-list.json", "qna-answer-detail.json", "qna-answer-create.json");

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

    @Test
    void importsContractsResolvesSixScreensAndProducesPluginBundles() throws Exception {
        registryRepository.createTableIfNotExists();
        patternRepository.createTableIfNotExists();
        ruleSetRepository.createTableIfNotExists();
        screenSpecRepository.createTableIfNotExists();

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
        try {
            KrdsRuntimeContractImportService.ImportResult imported = importer.importContracts(runtimeContracts);
            assertThat(imported.patternCount()).isEqualTo(4);
            assertThat(registryRepository.findVersion(profileId, registryVersion)).contains(registry);
            assertThat(ruleSetRepository.findPublished(profileId, registryVersion)).contains(publishedRules);

            KrdsComponentResolutionService resolver = new KrdsComponentResolutionService(
                    ruleSetRepository, patternRepository, new ScreenSemanticNormalizer(),
                    new ScreenPatternValidator(), new ComponentRoleResolver(), new VariantRuleResolver(),
                    new OperationalTelemetry(new SimpleMeterRegistry()));
            FigmaExportBundleAssembler assembler = new FigmaExportBundleAssembler();
            FigmaScreenSpecSerializer serializer = new FigmaScreenSpecSerializer(objectMapper);
            FigmaScreenSpecValidator validator = new FigmaScreenSpecValidator();
            DesignSystemProfile profile = new DesignSystemProfile(
                    profileId, "KRDS Q&A Integration", profileVersion, registryVersion,
                    registry.library().fileKey(), DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
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
            for (String file : SCREEN_FILES) {
                FigmaScreenSpec expected = readScreen(file);
                FigmaNodeSpec semanticRoot = withoutResolution(expected.content());
                PageSpec page = pageFor(expected);
                KrdsComponentResolutionService.ResolutionResult result = resolver.resolve(
                        profileId, registry, page, expected.screenType(), LayoutDensity.STANDARD,
                        expected.viewport(), semanticRoot);
                KrdsComponentResolutionService.ResolutionResult repeated = resolver.resolve(
                        profileId, registry, page, expected.screenType(), LayoutDensity.STANDARD,
                        expected.viewport(), semanticRoot);
                resolverSuccessCount++;

                assertThat(componentResolutions(result.content()))
                        .as("Runtime Rule ID / Variant Key: %s", expected.screenId())
                        .containsExactlyInAnyOrderEntriesOf(componentResolutions(expected.content()));
                assertThat(componentContextHashes(result.content()))
                        .as("결정형 Context Hash: %s", expected.screenId())
                        .containsExactlyInAnyOrderEntriesOf(componentContextHashes(repeated.content()));
                assertThat(result.componentContractVersion())
                        .as("Registry version과 독립적인 Entry contractVersion: %s", expected.screenId())
                        .isEqualTo("2.1.0")
                        .isNotEqualTo(registryVersion);
                List<String> unresolved = unresolvedComponentIds(result.content());
                unresolvedCount += unresolved.size();
                assertThat(unresolved).as("Unresolved Component: %s", expected.screenId()).isEmpty();
                List<FigmaNodeSpec> searchPanels = nodesWithRole(result.content(), "search.panel");
                if (expected.semanticPattern() == ScreenPattern.CRUD_LIST) {
                    assertThat(searchPanels).singleElement().satisfies(searchPanel -> {
                        assertThat(searchPanel.nodeType()).isEqualTo(FigmaNodeSpec.NodeType.COMPONENT);
                        assertThat(searchPanel.type()).isEqualTo("krds.searchPanel");
                        assertThat(searchPanel.children()).isEmpty();
                        assertThat(searchPanel.componentResolution().ruleId()).isEqualTo("search-panel-default");
                    });
                } else {
                    assertThat(searchPanels).isEmpty();
                }

                String runtimeScreenId = expected.screenId() + "-it-" + suffix;
                storedScreenIds.add(runtimeScreenId);
                FigmaScreenSpec resolvedSpec = new FigmaScreenSpec(
                        runtimeScreenId, 1, "qna-suite-it-" + suffix, 1,
                        expected.screenType(), expected.layoutPattern(), expected.name(), expected.route(),
                        expected.viewport(), "APPROVED",
                        new FigmaScreenSpec.DesignSystemRef(profileId, profileVersion, registryVersion),
                        result.content(), List.of(), result.pattern(), result.screenPatternVersion(),
                        result.variantRuleSetVersion(), result.componentContractVersion());
                assertThat(validator.validate(resolvedSpec)).isEmpty();
                screenSpecRepository.save(resolvedSpec);
                assertThat(screenSpecRepository.findVersion(runtimeScreenId, 1)).contains(resolvedSpec);

                FigmaExportBundle bundle = assembler.assemble(resolvedSpec, profile, registry);
                String bundleJson = serializer.toJson(bundle);
                assertThat(bundle.metadata().figmaScreenSpecSchemaVersion())
                        .isEqualTo(FigmaScreenSpec.SCHEMA_VERSION_V2);
                assertThat(bundleJson).contains("\"componentResolution\"", "\"figma-screen-spec-v2\"");
                Files.writeString(outputDirectory.resolve(expected.screenId() + ".json"), bundleJson);
            }
            assertThat(resolverSuccessCount).isEqualTo(6);
            assertThat(unresolvedCount).isZero();
            assertThat(storedScreenIds).hasSize(6);
            try (var files = Files.list(outputDirectory)) {
                assertThat(files.filter(path -> path.getFileName().toString().endsWith(".json")).count())
                        .as("Plugin 입력 Root Frame Bundle 수")
                        .isEqualTo(6L);
            }
        } finally {
            storedScreenIds.forEach(screenId -> jdbcTemplate.update(
                    "DELETE FROM AI_FIGMA_SCREEN_SPEC WHERE SCREEN_ID = ?", screenId));
            jdbcTemplate.update("DELETE FROM AI_VARIANT_RULE_SET WHERE RULE_SET_ID = ? AND RULE_SET_VERSION = ?",
                    ruleSetId, ruleSetVersion);
            jdbcTemplate.update("DELETE FROM AI_COMPONENT_REGISTRY WHERE PROFILE_ID = ? AND REGISTRY_VERSION = ?",
                    profileId, registryVersion);
            patterns.forEach(pattern -> jdbcTemplate.update(
                    "DELETE FROM AI_SCREEN_PATTERN WHERE PATTERN_ID = ? AND PATTERN_VERSION = ?",
                    pattern.pattern().code(), pattern.version()));
        }
    }

    private FigmaScreenSpec readScreen(String file) throws Exception {
        ClassPathResource resource = new ClassPathResource("figma/contracts/qna/v2/" + file);
        try (var input = resource.getInputStream()) {
            return objectMapper.readValue(input, FigmaScreenSpec.class);
        }
    }

    private PageSpec pageFor(FigmaScreenSpec screen) {
        String template = switch (screen.semanticPattern()) {
            case CRUD_LIST -> "QNA_LIST";
            case CRUD_DETAIL -> "QNA_DETAIL";
            case CRUD_CREATE -> "QNA_CREATE";
            case CRUD_EDIT -> "QNA_EDIT";
        };
        List<String> actions = switch (screen.semanticPattern()) {
            case CRUD_LIST -> List.of("SEARCH");
            case CRUD_DETAIL -> List.of("LIST");
            case CRUD_CREATE -> List.of("CREATE", "LIST");
            case CRUD_EDIT -> List.of("UPDATE", "LIST");
        };
        return new PageSpec(screen.screenId(), template, List.of(), actions);
    }

    private FigmaNodeSpec withoutResolution(FigmaNodeSpec node) {
        return new FigmaNodeSpec(node.logicalNodeId(), node.nodeType(), node.type(), node.properties(),
                node.children().stream().map(this::withoutResolution).toList());
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
