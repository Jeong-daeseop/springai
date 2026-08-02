package com.krdevops.springai.service.figma;

import com.krdevops.springai.mapper.ComponentRegistryRepository;
import com.krdevops.springai.mapper.DesignSystemProfileRepository;
import com.krdevops.springai.mapper.FigmaScreenSpecRepository;
import com.krdevops.springai.mapper.ScreenSpecRepository;
import com.krdevops.springai.model.design.PageSpec;
import com.krdevops.springai.model.design.ScreenSpecification;
import com.krdevops.springai.model.designsystem.ComponentRegistry;
import com.krdevops.springai.model.designsystem.DesignSystemProfile;
import com.krdevops.springai.model.figma.FigmaExportBundle;
import com.krdevops.springai.model.figma.FigmaExportIssue;
import com.krdevops.springai.model.figma.FigmaExportResult;
import com.krdevops.springai.model.figma.FigmaNodeSpec;
import com.krdevops.springai.model.figma.FigmaScreenExportRequest;
import com.krdevops.springai.model.figma.FigmaScreenSpec;
import com.krdevops.springai.model.figma.FigmaScreenType;
import com.krdevops.springai.model.figma.LayoutPattern;
import com.krdevops.springai.service.designsystem.ComponentRegistryResolver;
import com.krdevops.springai.service.figma.builder.FigmaScreenBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
            com.krdevops.springai.service.DesignArtifactService artifactService) {
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
                bundleAssembler, serializer, null);
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
        FigmaNodeSpec content = builder.build(screenSpecification, page, idFactory);

        List<FigmaExportIssue> issues = new ArrayList<>();
        DesignSystemProfile profile = resolveProfile(request.designSystemProfileId(), issues);
        checkComponentRegistry(profile, content, issues);

        String screenId = page.id();
        int screenVersion = nextScreenVersion(screenId);
        String viewport = request.viewport() == null || request.viewport().isBlank() ? "DESKTOP" : request.viewport();

        FigmaScreenSpec spec = new FigmaScreenSpec(
                screenId, screenVersion, screenSpecification.id(), screenSpecification.version(),
                screenType, layoutPattern, screenSpecification.screenName(), null, viewport,
                screenSpecification.status(),
                new FigmaScreenSpec.DesignSystemRef(profile.id(), profile.version(), profile.registryVersion()),
                content, List.of());

        issues.addAll(specValidator.validate(spec));
        FigmaScreenSpec finalSpec = new FigmaScreenSpec(
                spec.screenId(), spec.screenVersion(), spec.screenSpecificationId(), spec.screenSpecificationVersion(),
                spec.screenType(), spec.layoutPattern(), spec.name(), spec.route(), spec.viewport(), spec.status(),
                spec.designSystem(), spec.content(), issues);

        figmaScreenSpecRepository.save(finalSpec);

        FigmaExportResult.Status status = resultStatus(issues);
        LocalDateTime generatedAt = LocalDateTime.now();
        FigmaExportResult.ArtifactRef artifactRef = null;
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
        if (result.figmaScreenSpec() == null) {
            throw new IllegalStateException(
                    "FigmaScreenSpec 생성에 실패해 Bundle을 만들 수 없습니다: " + result.issues());
        }
        FigmaScreenSpec.DesignSystemRef designSystem = result.figmaScreenSpec().designSystem();
        DesignSystemProfile profile = profileRepository.findLatest(designSystem.profileId())
                .orElseGet(() -> defaultProfile(designSystem.profileId()));
        ComponentRegistry registry = registryRepository.findVersion(profile.id(), profile.registryVersion()).orElse(null);
        return bundleAssembler.assemble(result.figmaScreenSpec(), profile, registry);
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
        return bundleAssembler.assemble(spec, profile, registry);
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

    /** R2-012: 요청한 DesignSystemProfile이 없으면 빈 기본 Profile로 대체하고 경고를 남긴다. */
    private DesignSystemProfile resolveProfile(String requestedProfileId, List<FigmaExportIssue> issues) {
        String profileId = requestedProfileId == null || requestedProfileId.isBlank()
                ? DEFAULT_PROFILE_ID : requestedProfileId;
        return profileRepository.findLatest(profileId).orElseGet(() -> {
            issues.add(new FigmaExportIssue("PROFILE_NOT_FOUND",
                    FigmaExportIssue.Severity.WARNING,
                    "DesignSystemProfile을 찾을 수 없어 기본값을 사용합니다: " + profileId,
                    null, null, null));
            return defaultProfile(profileId);
        });
    }

    private DesignSystemProfile defaultProfile(String profileId) {
        return new DesignSystemProfile(
                profileId, profileId, "0.0-default", "0.0-default", null,
                DesignSystemProfile.Status.DRAFT, Map.of(), Map.of());
    }

    /** R2-013/R4-024: 화면 생성 전 논리 타입을 직접·alias·replacement 순으로 해석한다. */
    private void checkComponentRegistry(DesignSystemProfile profile, FigmaNodeSpec content, List<FigmaExportIssue> issues) {
        ComponentRegistry registry = registryRepository.findVersion(profile.id(), profile.registryVersion()).orElse(null);
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
                        FigmaExportIssue.Severity.WARNING,
                        "Registry에서 해석할 수 없는 컴포넌트 타입입니다(Plugin이 fallback 노드로 생성해야 함): " + type,
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
        boolean hasFatal = issues.stream().anyMatch(i -> i.severity() == FigmaExportIssue.Severity.FATAL);
        if (hasFatal) {
            return FigmaExportResult.Status.FAILED;
        }
        boolean hasError = issues.stream().anyMatch(i -> i.severity() == FigmaExportIssue.Severity.ERROR);
        return hasError ? FigmaExportResult.Status.PARTIAL : FigmaExportResult.Status.SUCCESS;
    }
}
