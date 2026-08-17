package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenPatternRepository;
import com.krdevops.springai.mapper.VariantRuleSetRepository;
import com.krdevops.springai.mapper.ComponentRegistrySnapshotV3Repository;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.designsystem.ScreenPatternDefinition;
import com.krdevops.springai.model.designsystem.VariantRuleSet;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.service.designsystem.ComponentRegistryResolver;
import com.krdevops.springai.service.designsystem.ResolvedComponentRegistryService;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * R2-008: 승인된 ScreenSpecification을 FigmaScreenSpec으로 변환하는 애플리케이션 서비스(08번 §7.3).
 * 처리 순서: ScreenSpecification 조회 → PageSpec 선택 → screenType/layoutPattern 판정 →
 * Builder 선택·트리 생성 → DesignSystemProfile/Registry 조회(R2-012/013) → 검증(R2-009) → 저장.
 */
@Service
public class FigmaScreenExportService {

    private static final String DEFAULT_PROFILE_ID = "krds";

    private final ScreenSpecRepository screenSpecRepository;
    private final FigmaScreenBuilderRegistry builderRegistry;
    private final FigmaScreenTypeResolver typeResolver;
    private final LogicalNodeIdFactory idFactory;
    private final FigmaScreenSpecValidator specValidator;
    private final DesignSystemProfileRepository profileRepository;
    private final ComponentRegistryRepository registryRepository;
    private final FigmaScreenSpecRepository figmaScreenSpecRepository;
    private final FigmaExportBundleAssembler bundleAssembler;
    private final FigmaScreenSpecSerializer serializer;
    private final com.krdevops.springai.service.DesignArtifactService artifactService;
    private final KrdsComponentResolutionService componentResolutionService;
    private final FigmaInventoryExportGate inventoryExportGate;
    private final ScreenPatternRepository screenPatternRepository;
    private final VariantRuleSetRepository variantRuleSetRepository;
    private final ComponentRegistrySnapshotV3Repository registryV3Repository;
    private final ResolvedComponentRegistryService resolvedRegistryService;

    @org.springframework.beans.factory.annotation.Autowired
    public FigmaScreenExportService(
            ScreenSpecRepository screenSpecRepository,
            FigmaScreenBuilderRegistry builderRegistry,
            FigmaScreenTypeResolver typeResolver,
            LogicalNodeIdFactory idFactory,
            FigmaScreenSpecValidator specValidator,
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryRepository registryRepository,
            FigmaScreenSpecRepository figmaScreenSpecRepository,
            FigmaExportBundleAssembler bundleAssembler,
            FigmaScreenSpecSerializer serializer,
            com.krdevops.springai.service.DesignArtifactService artifactService,
            KrdsComponentResolutionService componentResolutionService,
            FigmaInventoryExportGate inventoryExportGate,
            ScreenPatternRepository screenPatternRepository,
            VariantRuleSetRepository variantRuleSetRepository,
            ComponentRegistrySnapshotV3Repository registryV3Repository,
            ResolvedComponentRegistryService resolvedRegistryService) {
        this.screenSpecRepository = screenSpecRepository;
        this.builderRegistry = builderRegistry;
        this.typeResolver = typeResolver;
        this.idFactory = idFactory;
        this.specValidator = specValidator;
        this.profileRepository = profileRepository;
        this.registryRepository = registryRepository;
        this.figmaScreenSpecRepository = figmaScreenSpecRepository;
        this.bundleAssembler = bundleAssembler;
        this.serializer = serializer;
        this.artifactService = artifactService;
        this.componentResolutionService = componentResolutionService;
        this.inventoryExportGate = inventoryExportGate;
        this.screenPatternRepository = screenPatternRepository;
        this.variantRuleSetRepository = variantRuleSetRepository;
        this.registryV3Repository = registryV3Repository;
        this.resolvedRegistryService = resolvedRegistryService;
    }

    /** 테스트와 기존 직접 생성 호출의 하위 호환 생성자. */
    public FigmaScreenExportService(
            ScreenSpecRepository screenSpecRepository,
            FigmaScreenBuilderRegistry builderRegistry,
            FigmaScreenTypeResolver typeResolver,
            LogicalNodeIdFactory idFactory,
            FigmaScreenSpecValidator specValidator,
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryRepository registryRepository,
            FigmaScreenSpecRepository figmaScreenSpecRepository,
            FigmaExportBundleAssembler bundleAssembler,
            FigmaScreenSpecSerializer serializer) {
        this(screenSpecRepository, builderRegistry, typeResolver, idFactory, specValidator,
                profileRepository, registryRepository, figmaScreenSpecRepository,
                bundleAssembler, serializer, null, null, null, null, null, null, null);
    }

    /** 결정형 Resolution 서비스 도입 전 Artifact 저장 테스트 호환. */
    public FigmaScreenExportService(
            ScreenSpecRepository screenSpecRepository,
            FigmaScreenBuilderRegistry builderRegistry,
            FigmaScreenTypeResolver typeResolver,
            LogicalNodeIdFactory idFactory,
            FigmaScreenSpecValidator specValidator,
            DesignSystemProfileRepository profileRepository,
            ComponentRegistryRepository registryRepository,
            FigmaScreenSpecRepository figmaScreenSpecRepository,
            FigmaExportBundleAssembler bundleAssembler,
            FigmaScreenSpecSerializer serializer,
            com.krdevops.springai.service.DesignArtifactService artifactService) {
        this(screenSpecRepository, builderRegistry, typeResolver, idFactory, specValidator,
                profileRepository, registryRepository, figmaScreenSpecRepository,
                bundleAssembler, serializer, artifactService, null, null, null, null, null, null);
    }

    public FigmaExportResult export(FigmaScreenExportRequest request) {
        ScreenSpecification screenSpecification = resolveScreenSpecification(request);
        if (screenSpecification.status() != com.krdevops.springai.model.design.ScreenSpecStatus.APPROVED) {
            throw new IllegalArgumentException(
                    "APPROVED ScreenSpecification만 FigmaScreenSpec으로 내보낼 수 있습니다: "
                            + screenSpecification.id() + " (status=" + screenSpecification.status() + ")");
        }
        PageSpec page = selectPage(screenSpecification, request.pageId());

        FigmaScreenType screenType = typeResolver.resolveScreenType(page, screenSpecification);
        LayoutPattern layoutPattern = typeResolver.resolveLayoutPattern(screenSpecification);
        FigmaScreenBuilder builder = builderRegistry.builderFor(screenType);
        List<FigmaExportIssue> issues = new ArrayList<>();
        String screenId = page.id();
        int screenVersion = nextScreenVersion(screenId);
        String viewport = request.viewport() == null || request.viewport().isBlank() ? "DESKTOP" : request.viewport();
        FigmaNodeSpec content = builder.build(screenSpecification, page, idFactory);
        DesignSystemProfile profile;
        ComponentRegistry registry;
        KrdsComponentResolutionService.ResolutionResult resolution = null;
        try {
            profile = resolvePublishedProfile(request.designSystemProfileId());
            registry = registryRepository.findVersion(profile.id(), profile.registryVersion())
                    .orElseThrow(() -> new IllegalStateException(
                            "REGISTRY_NOT_FOUND: " + profile.id() + "/" + profile.registryVersion()));
            if (componentResolutionService == null) {
                checkComponentRegistry(registry, content, issues);
            } else {
                resolution = componentResolutionService.resolve(
                        profile.id(), registry, page, screenType,
                        screenSpecification.layoutDensity(), viewport, content);
                content = resolution.content();
            }
            if (inventoryExportGate != null) {
                issues.addAll(inventoryExportGate.validate(registry, content, request.exportMode()));
                if (hasBlockingIssues(issues)) {
                    return failedResult(screenId, screenVersion, issues, LocalDateTime.now());
                }
            }
        } catch (IllegalStateException | IllegalArgumentException exception) {
            issues.add(new FigmaExportIssue(
                    failureCode(exception), FigmaExportIssue.Severity.FATAL,
                    exception.getMessage(), screenId, null, null));
            return failedResult(screenId, screenVersion, issues, LocalDateTime.now());
        }

        FigmaScreenSpec spec = resolution == null
                ? new FigmaScreenSpec(
                        screenId, screenVersion, screenSpecification.id(), screenSpecification.version(),
                        screenType, layoutPattern, screenSpecification.screenName(), null, viewport,
                        screenSpecification.status().name(),
                        new FigmaScreenSpec.DesignSystemRef(profile.id(), profile.version(), profile.registryVersion()),
                        content, List.of())
                : new FigmaScreenSpec(
                        screenId, screenVersion, screenSpecification.id(), screenSpecification.version(),
                        screenType, layoutPattern, screenSpecification.screenName(), null, viewport,
                        screenSpecification.status().name(),
                        new FigmaScreenSpec.DesignSystemRef(profile.id(), profile.version(), profile.registryVersion()),
                        content, List.of(), resolution.pattern(), resolution.screenPatternVersion(),
                        resolution.variantRuleSetVersion(), resolution.componentContractVersion());

        issues.addAll(specValidator.validate(spec));
        FigmaScreenSpec finalSpec = new FigmaScreenSpec(
                spec.screenId(), spec.screenVersion(), spec.screenSpecificationId(), spec.screenSpecificationVersion(),
                spec.screenType(), spec.layoutPattern(), spec.name(), spec.route(), spec.viewport(), spec.status(),
                spec.designSystem(), spec.content(), issues, spec.semanticPattern(), spec.screenPatternVersion(),
                spec.variantRuleSetVersion(), spec.componentContractVersion());

        FigmaExportResult.Status status = resultStatus(issues);
        LocalDateTime generatedAt = LocalDateTime.now();
        FigmaExportResult.ArtifactRef artifactRef = null;
        if (hasBlockingIssues(issues)) {
            return failedResult(finalSpec.screenId(), finalSpec.screenVersion(), issues, generatedAt);
        }

        figmaScreenSpecRepository.save(finalSpec);
        if (artifactService != null) {
            com.krdevops.springai.service.DesignArtifactService.FigmaExportArtifact artifact =
                    artifactService.saveFigmaExport(finalSpec, status, issues, generatedAt);
            artifactRef = new FigmaExportResult.ArtifactRef(
                    artifact.artifactId(), artifact.relativePath());
        }
        return new FigmaExportResult(status, finalSpec, issues, generatedAt, artifactRef);
    }

    public Optional<FigmaScreenSpec> findLatest(String screenId) {
        return figmaScreenSpecRepository.findLatest(screenId);
    }

    public Optional<FigmaScreenSpec> findVersion(String screenId, int version) {
        return figmaScreenSpecRepository.findVersion(screenId, version);
    }

    /** R2-032: DEC-10=FILE 기본값 기준 다운로드용 FigmaExportBundle을 조립한다. */
    public FigmaExportBundle exportBundle(FigmaScreenExportRequest request) {
        FigmaExportResult result = export(request);
        if (result.status() != FigmaExportResult.Status.SUCCESS || result.figmaScreenSpec() == null) {
            throw new IllegalStateException(
                    "FigmaScreenSpec 생성에 실패해 Bundle을 만들 수 없습니다: " + result.issues());
        }
        FigmaScreenSpec.DesignSystemRef designSystem = result.figmaScreenSpec().designSystem();
        DesignSystemProfile profile = profileRepository
                .findVersion(designSystem.profileId(), designSystem.profileVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "PROFILE_VERSION_NOT_FOUND: " + designSystem.profileId()
                                + "/" + designSystem.profileVersion()));
        ComponentRegistry registry = registryRepository
                .findVersion(designSystem.profileId(), designSystem.registryVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "REGISTRY_NOT_FOUND: " + designSystem.profileId()
                                + "/" + designSystem.registryVersion()));
        return assembleVersionedBundle(result.figmaScreenSpec(), profile, registry);
    }

    /** R2-032: 위 Bundle을 파일 다운로드 응답 본문으로 쓸 JSON 문자열로 직렬화한다. */
    public String exportBundleAsJson(FigmaScreenExportRequest request) {
        return serializer.toJson(exportBundle(request));
    }

    /** R6: 이미 생성·저장된 최신 화면을 Plugin 입력 Bundle로 조립한다. */
    public FigmaExportBundle findLatestBundle(String screenId) {
        FigmaScreenSpec spec = findLatest(screenId)
                .orElseThrow(() -> new IllegalArgumentException("FigmaScreenSpec을 찾을 수 없습니다: " + screenId));
        return assembleStoredBundle(spec);
    }

    /** R6: 특정 화면 버전을 Plugin 입력 Bundle로 조립한다. */
    public FigmaExportBundle findBundleVersion(String screenId, int version) {
        FigmaScreenSpec spec = findVersion(screenId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FigmaScreenSpec 버전을 찾을 수 없습니다: " + screenId + " v" + version));
        return assembleStoredBundle(spec);
    }

    public String findLatestBundleAsJson(String screenId) {
        return serializer.toJson(findLatestBundle(screenId));
    }

    public String findBundleVersionAsJson(String screenId, int version) {
        return serializer.toJson(findBundleVersion(screenId, version));
    }

    /** SSOT 전환 경로: Registry v3가 없거나 검증에 실패하면 Legacy Bundle로 되돌아가지 않고 차단한다. */
    public FigmaExportBundle findLatestSsotBundle(String screenId) {
        FigmaScreenSpec spec = findLatest(screenId)
                .orElseThrow(() -> new IllegalArgumentException("FigmaScreenSpec을 찾을 수 없습니다: " + screenId));
        return assembleStoredSsotBundle(spec);
    }

    public FigmaExportBundle findSsotBundleVersion(String screenId, int version) {
        FigmaScreenSpec spec = findVersion(screenId, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "FigmaScreenSpec 버전을 찾을 수 없습니다: " + screenId + " v" + version));
        return assembleStoredSsotBundle(spec);
    }

    public String findLatestSsotBundleAsJson(String screenId) {
        return serializer.toJson(findLatestSsotBundle(screenId));
    }

    public String findSsotBundleVersionAsJson(String screenId, int version) {
        return serializer.toJson(findSsotBundleVersion(screenId, version));
    }

    /** 저장된 Spec의 기존 Issue와 현재 의미 검증 결과를 함께 반환한다. */
    public List<FigmaExportIssue> validateStored(String screenId, Integer version) {
        FigmaScreenSpec spec = version == null
                ? findLatest(screenId).orElseThrow(() -> new IllegalArgumentException(
                        "FigmaScreenSpec을 찾을 수 없습니다: " + screenId))
                : findVersion(screenId, version).orElseThrow(() -> new IllegalArgumentException(
                        "FigmaScreenSpec 버전을 찾을 수 없습니다: " + screenId + " v" + version));
        List<FigmaExportIssue> issues = new ArrayList<>(spec.issues());
        for (FigmaExportIssue issue : specValidator.validate(spec)) {
            boolean duplicate = issues.stream().anyMatch(existing ->
                    existing.code().equals(issue.code())
                            && java.util.Objects.equals(existing.logicalNodeId(), issue.logicalNodeId()));
            if (!duplicate) {
                issues.add(issue);
            }
        }
        return List.copyOf(issues);
    }

    private FigmaExportBundle assembleStoredBundle(FigmaScreenSpec spec) {
        FigmaScreenSpec.DesignSystemRef reference = spec.designSystem();
        DesignSystemProfile profile = profileRepository.findVersion(reference.profileId(), reference.profileVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "DesignSystemProfile 버전을 찾을 수 없습니다: " + reference.profileId()
                                + " v" + reference.profileVersion()));
        ComponentRegistry registry = registryRepository.findVersion(reference.profileId(), reference.registryVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ComponentRegistry 버전을 찾을 수 없습니다: " + reference.profileId()
                                + " v" + reference.registryVersion()));
        return assembleVersionedBundle(spec, profile, registry);
    }

    private FigmaExportBundle assembleStoredSsotBundle(FigmaScreenSpec spec) {
        if (registryV3Repository == null || resolvedRegistryService == null) {
            throw new IllegalStateException("SSOT_RESOLVER_NOT_CONFIGURED: Registry v3 Resolver가 없습니다.");
        }
        FigmaScreenSpec.DesignSystemRef reference = spec.designSystem();
        DesignSystemProfile profile = profileRepository.findVersion(reference.profileId(), reference.profileVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "DesignSystemProfile 버전을 찾을 수 없습니다: " + reference.profileId()
                                + " v" + reference.profileVersion()));
        ComponentRegistry legacyRegistry = registryRepository
                .findVersion(reference.profileId(), reference.registryVersion())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Legacy ComponentRegistry 버전을 찾을 수 없습니다: " + reference.profileId()
                                + " v" + reference.registryVersion()));
        var registryV3 = registryV3Repository.findVersion(reference.profileId(), reference.registryVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "REGISTRY_V3_NOT_FOUND: " + reference.profileId() + "/" + reference.registryVersion()));
        Set<String> requiredLogicalTypes = new HashSet<>();
        collectComponentTypes(spec.content(), requiredLogicalTypes);
        var resolved = resolvedRegistryService.resolve(registryV3, requiredLogicalTypes);

        if (spec.semanticPattern() == null) {
            throw new IllegalStateException("SSOT_BUNDLE_REQUIRES_V2_SCREEN_SPEC: Pattern Snapshot이 필요합니다.");
        }
        if (screenPatternRepository == null || variantRuleSetRepository == null) {
            throw new IllegalStateException("BUNDLE_SNAPSHOT_REPOSITORY_MISSING: v2 Bundle Snapshot 저장소가 없습니다.");
        }
        ScreenPatternDefinition pattern = screenPatternRepository
                .findVersion(spec.semanticPattern(), spec.screenPatternVersion())
                .orElseThrow(() -> new IllegalStateException("SCREEN_PATTERN_VERSION_NOT_FOUND: "
                        + spec.semanticPattern().code() + "/" + spec.screenPatternVersion()));
        VariantRuleSet ruleSet = variantRuleSetRepository.findPublishedVersion(
                        reference.profileId(), reference.registryVersion(), spec.variantRuleSetVersion())
                .orElseThrow(() -> new IllegalStateException("PUBLISHED_RULE_SET_VERSION_NOT_FOUND: "
                        + reference.profileId() + "/" + reference.registryVersion()
                        + "/" + spec.variantRuleSetVersion()));
        return bundleAssembler.assemble(spec, profile, legacyRegistry, pattern, ruleSet, resolved);
    }

    private FigmaExportBundle assembleVersionedBundle(
            FigmaScreenSpec spec, DesignSystemProfile profile, ComponentRegistry registry) {
        if (spec.semanticPattern() == null) {
            return bundleAssembler.assemble(spec, profile, registry);
        }
        if (screenPatternRepository == null || variantRuleSetRepository == null) {
            throw new IllegalStateException("BUNDLE_SNAPSHOT_REPOSITORY_MISSING: v2 Bundle Snapshot 저장소가 없습니다.");
        }
        ScreenPatternDefinition pattern = screenPatternRepository
                .findVersion(spec.semanticPattern(), spec.screenPatternVersion())
                .orElseThrow(() -> new IllegalStateException("SCREEN_PATTERN_VERSION_NOT_FOUND: "
                        + spec.semanticPattern().code() + "/" + spec.screenPatternVersion()));
        VariantRuleSet ruleSet = variantRuleSetRepository.findPublishedVersion(
                        spec.designSystem().profileId(), spec.designSystem().registryVersion(),
                        spec.variantRuleSetVersion())
                .orElseThrow(() -> new IllegalStateException("PUBLISHED_RULE_SET_VERSION_NOT_FOUND: "
                        + spec.designSystem().profileId() + "/" + spec.designSystem().registryVersion()
                        + "/" + spec.variantRuleSetVersion()));
        return bundleAssembler.assemble(spec, profile, registry, pattern, ruleSet);
    }

    private ScreenSpecification resolveScreenSpecification(FigmaScreenExportRequest request) {
        if (request.screenSpecificationVersion() != null) {
            return screenSpecRepository.findVersion(request.screenSpecificationId(), request.screenSpecificationVersion())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "ScreenSpecification 버전을 찾을 수 없습니다: " + request.screenSpecificationId()
                                    + " v" + request.screenSpecificationVersion()));
        }
        return screenSpecRepository.findLatest(request.screenSpecificationId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "ScreenSpecification을 찾을 수 없습니다: " + request.screenSpecificationId()));
    }

    private PageSpec selectPage(ScreenSpecification screenSpecification, String pageId) {
        return screenSpecification.pages().stream()
                .filter(p -> p.id().equals(pageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "PageSpec을 찾을 수 없습니다: " + pageId + " (screen=" + screenSpecification.id() + ")"));
    }

    private DesignSystemProfile resolvePublishedProfile(String requestedProfileId) {
        String profileId = requestedProfileId == null || requestedProfileId.isBlank()
                ? DEFAULT_PROFILE_ID : requestedProfileId;
        DesignSystemProfile profile = profileRepository.findLatest(profileId)
                .orElseThrow(() -> new IllegalStateException("PROFILE_NOT_FOUND: " + profileId));
        if (profile.status() != DesignSystemProfile.Status.PUBLISHED) {
            throw new IllegalStateException("PROFILE_NOT_PUBLISHED: " + profileId + "/" + profile.version());
        }
        return profile;
    }

    /** R2-013/R4-024: 화면 생성 전 논리 타입을 직접·alias·replacement 순으로 해석한다. */
    private void checkComponentRegistry(ComponentRegistry registry, FigmaNodeSpec content, List<FigmaExportIssue> issues) {
        ComponentRegistryResolver resolver = new ComponentRegistryResolver();
        Set<String> usedTypes = new HashSet<>();
        collectComponentTypes(content, usedTypes);
        for (String type : usedTypes) {
            if (!type.startsWith("krds.") && !type.startsWith("egov.")) {
                continue;
            }
            ComponentRegistryResolver.Resolution resolution = resolver.resolve(registry, type);
            if (!resolution.resolved()) {
                issues.add(new FigmaExportIssue("COMPONENT_NOT_IN_REGISTRY",
                        FigmaExportIssue.Severity.FATAL,
                        "Registry에서 해석할 수 없는 필수 컴포넌트 타입입니다: " + type,
                        null, null, null));
            } else if (resolution.kind() != ComponentRegistryResolver.ResolutionKind.DIRECT) {
                issues.add(new FigmaExportIssue("COMPONENT_REGISTRY_REDIRECT",
                        FigmaExportIssue.Severity.WARNING,
                        type + "가 " + resolution.resolvedLogicalType() + "로 대체 해석되었습니다.",
                        null, null, null));
            }
        }
    }

    private void collectComponentTypes(FigmaNodeSpec node, Set<String> accumulator) {
        if (node.nodeType() == FigmaNodeSpec.NodeType.COMPONENT) {
            accumulator.add(node.type());
        }
        for (FigmaNodeSpec child : node.children()) {
            collectComponentTypes(child, accumulator);
        }
    }

    private int nextScreenVersion(String screenId) {
        return figmaScreenSpecRepository.findLatest(screenId)
                .map(spec -> spec.screenVersion() + 1)
                .orElse(1);
    }

    private FigmaExportResult.Status resultStatus(List<FigmaExportIssue> issues) {
        if (hasBlockingIssues(issues)) {
            return FigmaExportResult.Status.FAILED;
        }
        return FigmaExportResult.Status.SUCCESS;
    }

    private boolean hasBlockingIssues(List<FigmaExportIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == FigmaExportIssue.Severity.FATAL
                || issue.severity() == FigmaExportIssue.Severity.ERROR);
    }

    private FigmaExportResult failedResult(
            String screenId,
            int screenVersion,
            List<FigmaExportIssue> issues,
            LocalDateTime generatedAt
    ) {
        FigmaExportResult.ArtifactRef artifactRef = null;
        if (artifactService != null) {
            com.krdevops.springai.service.DesignArtifactService.FigmaExportArtifact artifact =
                    artifactService.saveFigmaExportFailureReport(
                            screenId, screenVersion, issues, generatedAt);
            artifactRef = new FigmaExportResult.ArtifactRef(
                    artifact.artifactId(), artifact.relativePath());
        }
        return new FigmaExportResult(FigmaExportResult.Status.FAILED, null,
                List.copyOf(issues), generatedAt, artifactRef);
    }

    private String failureCode(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "FIGMA_EXPORT_PREFLIGHT_FAILED";
        String candidate = message.split(":", 2)[0].trim();
        return candidate.matches("[A-Z][A-Z0-9_]*")
                ? candidate : "FIGMA_EXPORT_PREFLIGHT_FAILED";
    }
}
