package com.krdevops.springai.service.designsystem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaLibraryInventoryRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.ComponentRegistryEntry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.FigmaLibraryInventorySnapshot;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecStatus;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.service.ScreenSpecValidator;
import com.krdevops.springai.service.figma.FigmaScreenBuilderRegistry;
import com.krdevops.springai.service.figma.FigmaScreenSpecValidator;
import com.krdevops.springai.service.figma.FigmaScreenTypeResolver;
import com.krdevops.springai.service.figma.KrdsComponentResolutionService;
import com.krdevops.springai.service.figma.LogicalNodeIdFactory;
import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator.ActualProperty;
import com.krdevops.springai.service.designsystem.FigmaPropertyDriftValidator.LibraryComponentSnapshot;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Q&A KRDS 6화면 실행에 필요한 전체 Fixture를 동일 버전 축으로 Bootstrap한다. */
@Service
public class KrdsQnaFixtureBootstrapService {
    public static final String INVENTORY_VERSION = "qna-fixture-inventory-2.1.0";
    public static final String SCREEN_SPECIFICATION_RESOURCE =
            "figma/contracts/qna/qna-screen-specification-v2.json";
    public static final int GENERATED_SCREEN_VERSION = 7;

    private final KrdsRuntimeContractImportService contractReader;
    private final ComponentRegistryRepository registryRepository;
    private final ScreenPatternRepository patternRepository;
    private final VariantRuleSetRepository ruleSetRepository;
    private final DesignSystemProfileRepository profileRepository;
    private final FigmaScreenSpecRepository screenRepository;
    private final FigmaLibraryInventoryRepository inventoryRepository;
    private final ScreenSpecRepository screenSpecRepository;
    private final FigmaScreenBuilderRegistry builderRegistry;
    private final FigmaScreenTypeResolver screenTypeResolver;
    private final LogicalNodeIdFactory idFactory;
    private final KrdsComponentResolutionService componentResolutionService;
    private final FigmaScreenSpecValidator figmaScreenSpecValidator;
    private final ScreenSpecValidator screenSpecValidator;
    private final ObjectMapper objectMapper;

    public KrdsQnaFixtureBootstrapService(
            KrdsRuntimeContractImportService contractReader,
            ComponentRegistryRepository registryRepository,
            ScreenPatternRepository patternRepository,
            VariantRuleSetRepository ruleSetRepository,
            DesignSystemProfileRepository profileRepository,
            FigmaScreenSpecRepository screenRepository,
            FigmaLibraryInventoryRepository inventoryRepository,
            ScreenSpecRepository screenSpecRepository,
            FigmaScreenBuilderRegistry builderRegistry,
            FigmaScreenTypeResolver screenTypeResolver,
            LogicalNodeIdFactory idFactory,
            KrdsComponentResolutionService componentResolutionService,
            FigmaScreenSpecValidator figmaScreenSpecValidator,
            ScreenSpecValidator screenSpecValidator,
            ObjectMapper objectMapper) {
        this.contractReader = contractReader;
        this.registryRepository = registryRepository;
        this.patternRepository = patternRepository;
        this.ruleSetRepository = ruleSetRepository;
        this.profileRepository = profileRepository;
        this.screenRepository = screenRepository;
        this.inventoryRepository = inventoryRepository;
        this.screenSpecRepository = screenSpecRepository;
        this.builderRegistry = builderRegistry;
        this.screenTypeResolver = screenTypeResolver;
        this.idFactory = idFactory;
        this.componentResolutionService = componentResolutionService;
        this.figmaScreenSpecValidator = figmaScreenSpecValidator;
        this.screenSpecValidator = screenSpecValidator;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
    }

    @Transactional
    public BootstrapResult bootstrap() {
        var contracts = contractReader.readDefaultQnaContracts();
        ComponentRegistry registry = contracts.registry();
        VariantRuleSet publishedRules = new VariantRuleSet(
                contracts.ruleSet().id(), contracts.ruleSet().version(),
                contracts.ruleSet().profileId(), contracts.ruleSet().registryVersion(),
                VariantRuleSet.Status.PUBLISHED, contracts.ruleSet().rules());

        saveRegistry(registry);
        contracts.patterns().forEach(patternRepository::saveImmutable);
        ruleSetRepository.saveImmutable(publishedRules);

        DesignSystemProfile profile = new DesignSystemProfile(
                registry.profileId(), "KRDS Q&A 6 Screens", registry.profileVersion(),
                registry.registryVersion(), registry.library().fileKey(),
                DesignSystemProfile.Status.PUBLISHED, Map.of(), Map.of());
        profileRepository.findVersion(profile.id(), profile.version()).ifPresent(existing -> {
            if (!existing.equals(profile)) throw new IllegalStateException(
                    "DESIGN_SYSTEM_PROFILE_VERSION_CONFLICT: " + profile.id() + "/" + profile.version());
        });
        if (profileRepository.findVersion(profile.id(), profile.version()).isEmpty()) profileRepository.save(profile);

        FigmaLibraryInventorySnapshot inventory = inventory(registry);
        inventoryRepository.saveImmutable(inventory);

        ScreenSpecification businessSpecification = readBusinessSpecification();
        ScreenSpecification validated = screenSpecValidator.validate(businessSpecification);
        if (validated.status() != ScreenSpecStatus.APPROVED || !validated.issues().isEmpty()) {
            throw new IllegalStateException("QNA_SCREEN_SPECIFICATION_INVALID: " + validated.issues());
        }
        screenSpecRepository.save(validated);

        List<String> screenIds = new ArrayList<>();
        for (PageSpec page : validated.pages()) {
            var screenType = screenTypeResolver.resolveScreenType(page, validated);
            FigmaNodeSpec semanticContent = builderRegistry.builderFor(screenType).build(validated, page, idFactory);
            var resolution = componentResolutionService.resolve(
                    profile.id(), registry, page, screenType, validated.layoutDensity(),
                    "DESKTOP", semanticContent);
            FigmaScreenSpec generated = new FigmaScreenSpec(
                    page.id(), GENERATED_SCREEN_VERSION, validated.id(), validated.version(), screenType,
                    screenTypeResolver.resolveLayoutPattern(validated), validated.screenName(), null,
                    "DESKTOP", "APPROVED",
                    new FigmaScreenSpec.DesignSystemRef(profile.id(), profile.version(), profile.registryVersion()),
                    resolution.content(), List.of(), resolution.pattern(), resolution.screenPatternVersion(),
                    resolution.variantRuleSetVersion(), resolution.componentContractVersion());
            var issues = figmaScreenSpecValidator.validate(generated);
            if (!issues.isEmpty()) {
                throw new IllegalStateException("QNA_FIGMA_SCREEN_SPEC_INVALID: " + page.id() + " " + issues);
            }
            assertResolved(generated.content());
            screenRepository.save(generated);
            screenIds.add(generated.screenId());
        }
        return new BootstrapResult(profile.id(), profile.version(), registry.registryVersion(),
                publishedRules.id(), publishedRules.version(), contracts.patterns().size(),
                INVENTORY_VERSION, List.copyOf(screenIds));
    }

    private ScreenSpecification readBusinessSpecification() {
        try (InputStream input = new ClassPathResource(SCREEN_SPECIFICATION_RESOURCE).getInputStream()) {
            return objectMapper.readValue(input, ScreenSpecification.class);
        } catch (Exception exception) {
            throw new IllegalStateException("Q&A ScreenSpecification Fixture를 읽을 수 없습니다.", exception);
        }
    }

    private void saveRegistry(ComponentRegistry registry) {
        registryRepository.findVersion(registry.profileId(), registry.registryVersion()).ifPresent(existing -> {
            if (!existing.equals(registry)) throw new IllegalStateException(
                    "COMPONENT_REGISTRY_VERSION_CONFLICT: " + registry.profileId() + "/" + registry.registryVersion());
        });
        if (registryRepository.findVersion(registry.profileId(), registry.registryVersion()).isEmpty()) {
            registryRepository.saveImmutable(registry);
        }
    }

    private FigmaLibraryInventorySnapshot inventory(ComponentRegistry registry) {
        Map<String, LibraryComponentSnapshot> components = new LinkedHashMap<>();
        registry.components().forEach((logicalType, entry) -> {
            Map<String, ActualProperty> properties = new LinkedHashMap<>();
            entry.properties().values().forEach(mapping -> properties.put(mapping.figmaProperty(),
                    new ActualProperty(mapping.type().name(), new LinkedHashSet<>(mapping.values().values()))));
            entry.variantAxes().values().forEach(axis -> properties.put(axis.figmaProperty(),
                    new ActualProperty(ComponentRegistryEntry.PropertyType.VARIANT.name(), axis.allowedValues())));
            components.put(logicalType, new LibraryComponentSnapshot(
                    entry.componentSetKey(), properties, entry.variants()));
        });
        return new FigmaLibraryInventorySnapshot(registry.profileId(), registry.registryVersion(),
                INVENTORY_VERSION, Instant.parse("2026-08-12T00:00:00Z"), components);
    }

    private void assertResolved(FigmaNodeSpec node) {
        if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT && node.componentResolution() == null) {
            throw new IllegalStateException("QNA_SCREEN_UNRESOLVED_COMPONENT: " + node.logicalNodeId());
        }
        node.children().forEach(this::assertResolved);
    }

    public record BootstrapResult(
            String profileId, String profileVersion, String registryVersion,
            String ruleSetId, String ruleSetVersion, int patternCount,
            String inventoryVersion, List<String> screenIds) {}
}
